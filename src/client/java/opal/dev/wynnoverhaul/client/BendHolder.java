package opal.dev.wynnoverhaul.client;

public interface BendHolder {
    void setLimbBend(float angle, long stamp);

    float getLimbBend();

    long getLimbBendStamp();

    void setSpine(float twist, float curl, float bend, long stamp);

    float getSpineTwist();

    float getSpineCurl();

    float getSpineBend();

    long getSpineStamp();
}
