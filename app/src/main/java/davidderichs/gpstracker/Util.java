package davidderichs.gpstracker;

public class Util {

    /**
     * Calculate sin^2(x).
     *
     * @param x
     *          x
     * @return sin^2(x)
     * @since 1.0
     */
    protected static double sinSquared(double x) {
        return Math.sin(x) * Math.sin(x);
    }


    /**
     * Calculate cos^2(x).
     *
     * @param x
     *          x
     * @return cos^2(x)
     * @since 1.0
     */
    protected static double cosSquared(double x) {
        return Math.cos(x) * Math.cos(x);
    }


    /**
     * Calculate tan^2(x).
     *
     * @param x
     *          x
     * @return tan^2(x)
     * @since 1.0
     */
    protected static double tanSquared(double x) {
        return Math.tan(x) * Math.tan(x);
    }


    /**
     * Calculate sec(x).
     *
     * @param x
     *          x
     * @return sec(x)
     * @since 1.0
     */
    protected static double sec(double x) {
        return 1.0 / Math.cos(x);
    }
}
