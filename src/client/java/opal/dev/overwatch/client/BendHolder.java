package opal.dev.overwatch.client;

public interface BendHolder {
    void setLimbBend(float angle, long stamp);

    float getLimbBend();

    long getLimbBendStamp();
}
