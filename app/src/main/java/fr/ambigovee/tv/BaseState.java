package fr.ambigovee.tv;

final class BaseState {
    final int brightness;
    final int r;
    final int g;
    final int b;
    final int kelvin;

    BaseState(int brightness, int r, int g, int b, int kelvin) {
        this.brightness = clamp(brightness, 1, 100);
        this.r = clamp(r, 0, 255);
        this.g = clamp(g, 0, 255);
        this.b = clamp(b, 0, 255);
        this.kelvin = Math.max(0, kelvin);
    }

    static BaseState fromGovee(GoveeLan.GoveeState state) {
        return new BaseState(
                state.brightness,
                state.r,
                state.g,
                state.b,
                state.kelvin
        );
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
