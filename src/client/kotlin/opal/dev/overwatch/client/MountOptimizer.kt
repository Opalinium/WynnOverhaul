package opal.dev.overwatch.client

import kotlin.math.abs
import kotlin.math.ceil

enum class MountTrainMode { NORMAL, LESS_TRAINING, NO_TRAINING }

data class MountTierSolveResult(val feedCounts: Map<String, Int>, val totalFeeds: Int, val pointsAdded: IntArray)

data class MountOptimizerPhase(
    val label: String,
    val feedCounts: Map<String, Int>,
    val totalFeeds: Int,
    val pointsAdded: IntArray,
    val isTraining: Boolean = false,
)

data class MountOptimizerResult(val phases: List<MountOptimizerPhase>, val grandTotal: Int, val unsolvable: Set<Int>)

data class MountShoppingList(
    val phases: List<MountOptimizerPhase>,
    val grandTotal: Int,
    val unsolvable: Set<Int>,
    val allMaxed: Boolean,
    val noMaterialsAvailable: Boolean,
    val rawH: Int,
    val maxUnknown: Boolean = false,
    val trainable: List<String> = emptyList(),
)

object MountOptimizer {

    fun maxUsableTier(h: Int): Int = MountMaterials.TIER_THRESHOLDS.fold(-1) { best, t -> if (t <= h) t else best }

    private fun dominates(a: MountMaterialRow, b: MountMaterialRow): Boolean {
        var strictlyBetter = false
        for (s in 0 until 8) {
            val av = a.points[s]
            val bv = b.points[s]
            if (av < bv) return false
            if (av > bv) strictlyBetter = true
        }
        return strictlyBetter
    }

    fun candsSorted(tier: Int): List<MountMaterialRow> {
        val pool = MountMaterials.ALL.filter { it.tier <= tier }
        val pruned = pool.filterIndexed { idx, mat -> pool.indices.none { i -> i != idx && dominates(pool[i], mat) } }
        return pruned.sortedByDescending { it.points.sum() }
    }

    fun solveLP(cands: List<MountMaterialRow>, solvable: IntArray, nc: Int): DoubleArray {
        val active = ArrayList<Int>()
        for (s in 0 until 8) if (solvable[s] > 0) active.add(s)
        val m = active.size
        if (m == 0) return DoubleArray(nc)

        val nv = nc + 2 * m
        val bigM = 1e9
        val tab = Array(m + 1) { DoubleArray(nv + 1) }

        for (i in 0 until m) {
            val s = active[i]
            for (j in 0 until nc) tab[i][j] = cands[j].points[s].toDouble()
            tab[i][nc + i] = -1.0
            tab[i][nc + m + i] = 1.0
            tab[i][nv] = solvable[s].toDouble()
        }
        for (j in 0 until nc) tab[m][j] = 1.0
        for (i in 0 until m) tab[m][nc + m + i] = bigM
        for (i in 0 until m) for (j in 0..nv) tab[m][j] -= bigM * tab[i][j]

        val basis = IntArray(m) { nc + m + it }
        for (iter in 0 until 500) {
            var pivCol = -1
            var pivVal = -1e-9
            for (j in 0 until nv) if (tab[m][j] < pivVal) { pivVal = tab[m][j]; pivCol = j }
            if (pivCol < 0) break
            var pivRow = -1
            var minRatio = Double.POSITIVE_INFINITY
            for (i in 0 until m) {
                val e = tab[i][pivCol]
                if (e > 1e-9) {
                    val r = tab[i][nv] / e
                    if (r < minRatio - 1e-12) { minRatio = r; pivRow = i }
                }
            }
            if (pivRow < 0) break
            basis[pivRow] = pivCol
            val piv = tab[pivRow][pivCol]
            for (j in 0..nv) tab[pivRow][j] /= piv
            for (i in 0..m) {
                if (i == pivRow || abs(tab[i][pivCol]) < 1e-12) continue
                val f = tab[i][pivCol]
                for (j in 0..nv) tab[i][j] -= f * tab[pivRow][j]
            }
        }
        val x = DoubleArray(nc)
        for (i in 0 until m) if (basis[i] < nc) x[basis[i]] = tab[i][nv]
        return x
    }

    private fun solveSingleStat(cands: List<MountMaterialRow>, statIdx: Int, need: Int): MountTierSolveResult {
        var bestMat: MountMaterialRow? = null
        var bestCoeff = 0
        for (m in cands) {
            val c = m.points[statIdx]
            if (c > bestCoeff) { bestCoeff = c; bestMat = m }
        }
        val mat = bestMat
        if (mat == null || bestCoeff == 0) return MountTierSolveResult(emptyMap(), Int.MAX_VALUE, IntArray(8))
        val count = ceil(need.toDouble() / bestCoeff).toInt()
        val pointsAdded = IntArray(8) { s -> count * mat.points[s] }
        return MountTierSolveResult(mapOf(mat.name to count), count, pointsAdded)
    }

    private fun multiStartGreedy(cands: List<MountMaterialRow>, solvable: IntArray): IntArray {
        val nc = cands.size

        fun oneRun(forcedFirst: Int): IntArray {
            val rem = solvable.copyOf()
            val x = IntArray(nc)
            if (forcedFirst >= 0) {
                x[forcedFirst]++
                val b = cands[forcedFirst]
                for (s in 0 until 8) rem[s] = maxOf(rem[s] - b.points[s], 0)
            }
            for (iter in 0 until 2000) {
                if (rem.all { it <= 0 }) break
                var bestIdx = -1
                var bestRed = 0
                for (mi in 0 until nc) {
                    var red = 0
                    for (s in 0 until 8) red += minOf(rem[s], cands[mi].points[s])
                    if (red > bestRed) { bestRed = red; bestIdx = mi }
                }
                if (bestIdx < 0) break
                x[bestIdx]++
                for (s in 0 until 8) rem[s] = maxOf(rem[s] - cands[bestIdx].points[s], 0)
            }
            val surplus = IntArray(8)
            for (mi in 0 until nc) for (s in 0 until 8) surplus[s] += x[mi] * cands[mi].points[s]
            for (s in 0 until 8) surplus[s] -= solvable[s]
            var changed = true
            while (changed) {
                changed = false
                for (mi in 0 until nc) {
                    if (x[mi] == 0) continue
                    if ((0 until 8).all { s -> surplus[s] >= cands[mi].points[s] }) {
                        x[mi]--
                        for (s in 0 until 8) surplus[s] -= cands[mi].points[s]
                        changed = true
                    }
                }
            }
            return x
        }

        var bestX = oneRun(-1)
        var bestTotal = bestX.sum()
        for (fi in 0 until nc) {
            var red = 0
            for (s in 0 until 8) red += minOf(solvable[s], cands[fi].points[s])
            if (red == 0) continue
            val x = oneRun(fi)
            val total = x.sum()
            if (total < bestTotal) { bestTotal = total; bestX = x }
        }
        return bestX
    }

    fun solveTier(cands: List<MountMaterialRow>, needed: IntArray): MountTierSolveResult {
        val nc = cands.size

        val maxCoeff = IntArray(8)
        for (mat in cands) for (s in 0 until 8) maxCoeff[s] = maxOf(maxCoeff[s], mat.points[s])
        val solvable = IntArray(8) { s -> if (maxCoeff[s] > 0) needed[s] else 0 }

        fun lowerBound(residual: IntArray): Int {
            var lb = 0
            for (s in 0 until 8) if (residual[s] > 0 && maxCoeff[s] > 0) lb = maxOf(lb, ceil(residual[s].toDouble() / maxCoeff[s]).toInt())
            return lb
        }

        val lpX = solveLP(cands, solvable, nc)
        val initX = IntArray(nc) { i -> ceil(lpX[i] - 1e-9).toInt() }

        var improved = true
        while (improved) {
            improved = false
            for (i in 0 until nc) {
                if (initX[i] == 0) continue
                initX[i]--
                var ok = true
                for (s in 0 until 8) {
                    if (solvable[s] <= 0) continue
                    var cov = 0
                    for (j in 0 until nc) cov += initX[j] * cands[j].points[s]
                    if (cov < solvable[s]) { ok = false; break }
                }
                if (!ok) initX[i]++ else improved = true
            }
        }

        val greedyX = multiStartGreedy(cands, solvable)
        val greedyTotal = greedyX.sum()
        val lpTotal = initX.sum()
        val useGreedy = greedyTotal < lpTotal

        val finalX = (if (useGreedy) greedyX else initX).copyOf()
        var finalTotal = if (useGreedy) greedyTotal else lpTotal

        if (finalTotal <= 30 && solvable.any { it > 0 }) {
            val x = IntArray(nc)
            val memo = HashMap<String, Int>()
            val tStart = System.currentTimeMillis()

            fun dfs(level: Int, currentTotal: Int, residual: IntArray) {
                if (System.currentTimeMillis() - tStart > 300) return
                if (residual.all { it <= 0 }) {
                    if (currentTotal < finalTotal) {
                        finalTotal = currentTotal
                        for (i in 0 until nc) finalX[i] = x[i]
                    }
                    return
                }
                if (level == nc) return
                if (currentTotal + lowerBound(residual) >= finalTotal) return
                val key = "$level|${residual.joinToString(",")}"
                val memoVal = memo[key]
                if (memoVal != null && currentTotal + memoVal >= finalTotal) return
                val prevFinalTotal = finalTotal
                val mat = cands[level]
                var statMax = 0
                for (s in 0 until 8) {
                    val p = mat.points[s]
                    if (p > 0 && residual[s] > 0) statMax = maxOf(statMax, ceil(residual[s].toDouble() / p).toInt())
                }
                val xMax = minOf(statMax, finalTotal - currentTotal - 1)
                for (xi in 0..xMax) {
                    x[level] = xi
                    val newRes = IntArray(8) { s -> maxOf(residual[s] - xi * mat.points[s], 0) }
                    dfs(level + 1, currentTotal + xi, newRes)
                }
                x[level] = 0
                if (finalTotal < prevFinalTotal) {
                    val best = finalTotal - currentTotal
                    val existing = memo[key]
                    if (existing == null || existing > best) memo[key] = best
                }
            }

            dfs(0, 0, solvable.copyOf())
        }

        val feedCounts = LinkedHashMap<String, Int>()
        val pointsAdded = IntArray(8)
        for (i in 0 until nc) {
            if (finalX[i] > 0) {
                feedCounts[cands[i].name] = finalX[i]
                for (s in 0 until 8) pointsAdded[s] += finalX[i] * cands[i].points[s]
            }
        }
        return MountTierSolveResult(feedCounts, finalTotal, pointsAdded)
    }

    fun runOptimizer(
        startLevels: IntArray,
        remainingIn: IntArray,
        targetH: Int,
        mode: MountTrainMode,
        maxLevels: IntArray?,
        tierCap: Int?,
    ): MountOptimizerResult {
        val phases = ArrayList<MountOptimizerPhase>()
        val unsolvable = HashSet<Int>()
        var curLevels = startLevels.copyOf()
        var remaining = remainingIn.copyOf()
        var safety = 0

        while (remaining.any { it > 0 } && safety++ < 30) {
            val h = curLevels.max()
            val curTier = if (tierCap != null) minOf(tierCap, maxUsableTier(h)) else maxUsableTier(h)
            if (curTier == -1) break
            val cands = candsSorted(curTier)

            val maxCoeff = IntArray(8)
            for (mat in cands) for (s in 0 until 8) maxCoeff[s] = maxOf(maxCoeff[s], mat.points[s])
            for (s in 0 until 8) if (remaining[s] > 0 && maxCoeff[s] == 0) unsolvable.add(s)

            val stayResult = solveTier(cands, remaining)
            var bestCost = stayResult.totalFeeds
            var bestUnlockPhase: MountOptimizerPhase? = null
            var bestNewLevels: IntArray? = null
            var bestNewRemaining: IntArray? = null

            val unlockTiersAll = if (mode == MountTrainMode.NO_TRAINING) {
                emptyList()
            } else {
                MountMaterials.TIER_THRESHOLDS.filter { it > curTier && it <= targetH }
            }
            val unlockTiers = if (mode == MountTrainMode.LESS_TRAINING) unlockTiersAll.take(1) else unlockTiersAll

            for (targetTier in unlockTiers) {
                val targetCands = candsSorted(targetTier)
                for (uIdx in 0 until 8) {
                    val gap = targetTier - curLevels[uIdx]
                    if (gap <= 0 || maxCoeff[uIdx] == 0) continue
                    if (maxLevels != null && targetTier > maxLevels[uIdx]) continue

                    val unlockResult = solveSingleStat(cands, uIdx, gap)
                    if (unlockResult.totalFeeds == Int.MAX_VALUE) continue

                    val remainAfter = IntArray(8) { s -> maxOf(remaining[s] - unlockResult.pointsAdded[s], 0) }
                    val nextResult = solveTier(targetCands, remainAfter)
                    val totalCost = unlockResult.totalFeeds + nextResult.totalFeeds

                    if (totalCost < bestCost) {
                        bestCost = totalCost
                        bestUnlockPhase = MountOptimizerPhase(
                            label = "Tier $curTier -- raising ${MountMaterials.STATS[uIdx]} to unlock Tier $targetTier",
                            feedCounts = unlockResult.feedCounts,
                            totalFeeds = unlockResult.totalFeeds,
                            pointsAdded = unlockResult.pointsAdded,
                        )
                        bestNewLevels = IntArray(8) { s -> curLevels[s] + unlockResult.pointsAdded[s] }
                        bestNewRemaining = remainAfter
                    }
                }
            }

            val unlockPhase = bestUnlockPhase
            if (unlockPhase == null) {
                phases.add(MountOptimizerPhase("Tier $curTier", stayResult.feedCounts, stayResult.totalFeeds, stayResult.pointsAdded))
                break
            }

            phases.add(unlockPhase)
            val newLevels = bestNewLevels!!
            curLevels = if (maxLevels != null) IntArray(8) { s -> minOf(newLevels[s], maxLevels[s]) } else newLevels
            remaining = bestNewRemaining!!
        }

        return MountOptimizerResult(phases, phases.sumOf { it.totalFeeds }, unsolvable)
    }

    fun computeShoppingList(cur: IntArray, lim: IntArray, max: IntArray, mode: MountTrainMode, levelCap: Int?): MountShoppingList {
        val rawCur = cur.copyOf()
        val effCur = IntArray(8) { i -> maxOf(cur[i], minOf(lim[i], max[i])) }
        val remaining0 = IntArray(8) { i -> maxOf(max[i] - lim[i], 0) }
        var targetH = max.max()

        val rawH0 = rawCur.max()
        if (remaining0.all { it == 0 }) {
            return MountShoppingList(emptyList(), 0, emptySet(), allMaxed = true, noMaterialsAvailable = false, rawH = rawH0)
        }

        var effH = effCur.max()
        val tierCap = levelCap?.let { maxUsableTier(it) }
        if (levelCap != null) {
            targetH = minOf(targetH, levelCap)
            effH = minOf(effH, levelCap)
        }
        val rawTier = if (tierCap != null) minOf(tierCap, maxUsableTier(rawH0)) else maxUsableTier(rawH0)
        val effTier = maxUsableTier(effH)

        if (maxOf(rawTier, effTier) == -1) {
            return MountShoppingList(emptyList(), 0, emptySet(), allMaxed = false, noMaterialsAvailable = true, rawH = rawH0)
        }

        val maxLevels = max.copyOf()
        val planBase = runOptimizer(rawCur, remaining0, targetH, mode, maxLevels, tierCap)
        val trainHelps = mode != MountTrainMode.NO_TRAINING && effH > rawH0
        val planTrain = if (trainHelps) runOptimizer(effCur, remaining0, targetH, mode, maxLevels, tierCap) else null
        val planMultiTier = if (mode == MountTrainMode.NORMAL && rawTier > 1) {
            runOptimizer(IntArray(8) { 1 }, remaining0, targetH, MountTrainMode.NORMAL, maxLevels, tierCap)
        } else {
            null
        }

        val allPlans = listOfNotNull(planBase, planTrain, planMultiTier)
        val chosen = allPlans.minByOrNull { it.grandTotal }!!
        val useTrain = planTrain != null && chosen === planTrain

        val allPhases = ArrayList<MountOptimizerPhase>()
        if (useTrain) {
            val saved = planBase.grandTotal - planTrain.grandTotal
            val tierUnlock = effTier > rawTier
            val label = if (tierUnlock) {
                "Train your mount to at least level $effTier first (saves $saved feed${if (saved == 1) "" else "s"})"
            } else {
                "Train your stats to their current limits first (saves $saved feed${if (saved == 1) "" else "s"})"
            }
            allPhases.add(MountOptimizerPhase(label, emptyMap(), 0, IntArray(8), isTraining = true))
        }
        allPhases.addAll(chosen.phases)

        return MountShoppingList(allPhases, chosen.grandTotal, chosen.unsolvable, allMaxed = false, noMaterialsAvailable = false, rawH = rawH0)
    }
}
