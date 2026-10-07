package opal.dev.wynnoverhaul.client

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class AnimExpr private constructor(private val node: Node) {
    fun eval(env: FloatArray): Float = node.eval(env)

    private abstract class Node {
        abstract fun eval(env: FloatArray): Float
    }

    private class Num(val value: Float) : Node() {
        override fun eval(env: FloatArray) = value
    }

    private class Slot(val index: Int) : Node() {
        override fun eval(env: FloatArray) = env[index]
    }

    private class Neg(val inner: Node) : Node() {
        override fun eval(env: FloatArray) = -inner.eval(env)
    }

    private class Binary(val op: Char, val left: Node, val right: Node) : Node() {
        override fun eval(env: FloatArray): Float {
            val a = left.eval(env)
            val b = right.eval(env)
            return when (op) {
                '+' -> a + b
                '-' -> a - b
                '*' -> a * b
                else -> if (b == 0f) 0f else a / b
            }
        }
    }

    private class Call(val fn: Int, val args: Array<Node>) : Node() {
        override fun eval(env: FloatArray): Float {
            val a = args[0].eval(env)
            return when (fn) {
                SIN -> sin(a)
                COS -> cos(a)
                ABS -> abs(a)
                SQRT -> sqrt(max(0f, a))
                MIN -> min(a, args[1].eval(env))
                MAX -> max(a, args[1].eval(env))
                POW -> a.toDouble().pow(args[1].eval(env).toDouble()).toFloat()
                CLAMP -> a.coerceIn(args[1].eval(env), args[2].eval(env))
                else -> a + (args[1].eval(env) - a) * args[2].eval(env)
            }
        }
    }

    private class Parser(private val text: String, private val resolve: (String) -> Int) {
        private var pos = 0

        fun parseAll(): Node {
            val node = expression()
            skip()
            if (pos < text.length) fail("unexpected '${text[pos]}'")
            return node
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("$message at position ${pos + 1} in \"$text\"")

        private fun skip() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun expression(): Node {
            var left = term()
            while (true) {
                skip()
                val c = peek()
                if (c != '+' && c != '-') return left
                pos++
                left = Binary(c, left, term())
            }
        }

        private fun term(): Node {
            var left = unary()
            while (true) {
                skip()
                val c = peek()
                if (c != '*' && c != '/') return left
                pos++
                left = Binary(c, left, unary())
            }
        }

        private fun unary(): Node {
            skip()
            if (peek() == '-') {
                pos++
                return Neg(unary())
            }
            if (peek() == '+') {
                pos++
                return unary()
            }
            return atom()
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

        private fun atom(): Node {
            skip()
            val c = peek()
            if (c == '(') {
                pos++
                val inner = expression()
                skip()
                if (peek() != ')') fail("expected ')'")
                pos++
                return inner
            }
            if (c.isDigit() || c == '.') return number()
            if (c.isLetter() || c == '_') return identifier()
            fail(if (c == '\u0000') "unexpected end" else "unexpected '$c'")
        }

        private fun number(): Node {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (peek() == '-' || peek() == '+') pos++
                while (pos < text.length && text[pos].isDigit()) pos++
            }
            val value = text.substring(start, pos).toFloatOrNull() ?: fail("bad number")
            return Num(value)
        }

        private fun identifier(): Node {
            val start = pos
            while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_' || text[pos] == '.')) pos++
            val name = text.substring(start, pos)
            skip()
            if (peek() == '(') {
                val fn = FUNCTIONS[name] ?: fail("unknown function $name")
                pos++
                val args = ArrayList<Node>()
                skip()
                if (peek() != ')') {
                    while (true) {
                        args.add(expression())
                        skip()
                        if (peek() == ',') {
                            pos++
                            continue
                        }
                        break
                    }
                }
                if (peek() != ')') fail("expected ')'")
                pos++
                if (args.size != ARITY.getValue(name)) fail("$name takes ${ARITY.getValue(name)} arguments")
                return Call(fn, args.toTypedArray())
            }
            if (name == "pi") return Num(Math.PI.toFloat())
            val slot = resolve(name)
            if (slot < 0) fail("unknown variable $name")
            return Slot(slot)
        }
    }

    companion object {
        private const val SIN = 0
        private const val COS = 1
        private const val ABS = 2
        private const val SQRT = 3
        private const val MIN = 4
        private const val MAX = 5
        private const val POW = 6
        private const val CLAMP = 7
        private const val LERP = 8

        private val FUNCTIONS = mapOf(
            "sin" to SIN, "cos" to COS, "abs" to ABS, "sqrt" to SQRT, "min" to MIN,
            "max" to MAX, "pow" to POW, "clamp" to CLAMP, "lerp" to LERP,
        )

        private val ARITY = mapOf(
            "sin" to 1, "cos" to 1, "abs" to 1, "sqrt" to 1, "min" to 2,
            "max" to 2, "pow" to 2, "clamp" to 3, "lerp" to 3,
        )

        fun compile(text: String, resolve: (String) -> Int): AnimExpr = AnimExpr(Parser(text, resolve).parseAll())
    }
}
