package com.dillon.starsectormarines.ui.retained.svg;

import java.awt.geom.Path2D;

/**
 * Strict SVG 2 path-data scanner adapted from MoonLight Engine's SvgPathData.
 * The portable command grammar is retained; its engine geometry builder is
 * replaced by Java2D paths for shared canvas tessellation.
 */
final class SvgPathData {

    private static final String COMMANDS = "MmZzLlHhVvCcSsQqTtAa";

    private final String data;
    private final Path2D.Double builder = new Path2D.Double();

    private int at;

    private boolean open;

    private float currentX;
    private float currentY;
    private float subpathStartX;
    private float subpathStartY;

    private float controlX;
    private float controlY;

    private char previous;

    private SvgPathData(String data) {
        this.data = data;
    }

    public static Path2D.Double parse(String d) {
        if (d == null) {
            throw new IllegalArgumentException("path data is required");
        }
        return new SvgPathData(d).run();
    }

    private Path2D.Double run() {
        skipWhitespace();
        char command = 0;
        while (at < data.length()) {
            char letter = data.charAt(at);
            if (COMMANDS.indexOf(letter) >= 0) {
                if (command == 0 && letter != 'M' && letter != 'm') {
                    throw error("a moveto command (M or m) to open the path");
                }
                at++;
                command = letter;
                skipWhitespace();
            } else {
                // No letter, so this is the standard's implicit repetition of the previous command's
                // argument set. The separator that would have preceded a letter is a comma-wsp here.
                char repeated = repeatOf(command);
                if (repeated == 0) {
                    throw error(command == 0
                            ? "a moveto command (M or m) to open the path"
                            : "a path command");
                }
                command = repeated;
                skipCommaWhitespace();
            }
            argumentSet(command);
            skipWhitespace();
        }
        return builder;
    }

    private static char repeatOf(char command) {
        return switch (command) {
            case 'M' -> 'L';
            case 'm' -> 'l';
            case 'Z', 'z', 0 -> 0;
            default -> command;
        };
    }

    private void argumentSet(char command) {
        char verb = Character.toUpperCase(command);
        if (verb != 'M' && verb != 'Z') {
            // Before the relative origin is read, because reopening moves the current point.
            openSubpath();
        }
        boolean relative = Character.isLowerCase(command);
        float originX = relative ? currentX : 0f;
        float originY = relative ? currentY : 0f;
        switch (verb) {
            case 'M' -> {
                float x = (float) (originX + argument(true));
                float y = (float) (originY + argument(false));
                builder.moveTo(x, y);
                open = true;
                subpathStartX = x;
                subpathStartY = y;
                currentX = x;
                currentY = y;
            }
            case 'Z' -> {
                // A closepath with nothing open is the one command that does not reopen the
                // subpath: there is nothing to close, and reopening would leave a lone moveTo that
                // a round or square cap would draw as a dot the author never wrote.
                if (open) {
                    builder.closePath();
                    open = false;
                }
                currentX = subpathStartX;
                currentY = subpathStartY;
            }
            case 'L' -> {
                float x = (float) (originX + argument(true));
                float y = (float) (originY + argument(false));
                lineTo(x, y);
            }
            case 'H' -> {
                lineTo((float) (originX + argument(true)), currentY);
            }
            case 'V' -> {
                lineTo(currentX, (float) (originY + argument(true)));
            }
            case 'C' -> {
                float c1x = (float) (originX + argument(true));
                float c1y = (float) (originY + argument(false));
                float c2x = (float) (originX + argument(false));
                float c2y = (float) (originY + argument(false));
                cubicTo(c1x, c1y, c2x, c2y,
                        (float) (originX + argument(false)), (float) (originY + argument(false)));
            }
            case 'S' -> {
                boolean reflect = previous == 'C' || previous == 'c' || previous == 'S' || previous == 's';
                float c1x = reflect ? 2f * currentX - controlX : currentX;
                float c1y = reflect ? 2f * currentY - controlY : currentY;
                float c2x = (float) (originX + argument(true));
                float c2y = (float) (originY + argument(false));
                cubicTo(c1x, c1y, c2x, c2y,
                        (float) (originX + argument(false)), (float) (originY + argument(false)));
            }
            case 'Q' -> {
                float cx = (float) (originX + argument(true));
                float cy = (float) (originY + argument(false));
                quadraticTo(cx, cy,
                        (float) (originX + argument(false)), (float) (originY + argument(false)));
            }
            case 'T' -> {
                boolean reflect = previous == 'Q' || previous == 'q' || previous == 'T' || previous == 't';
                float cx = reflect ? 2f * currentX - controlX : currentX;
                float cy = reflect ? 2f * currentY - controlY : currentY;
                quadraticTo(cx, cy,
                        (float) (originX + argument(true)), (float) (originY + argument(false)));
            }
            case 'A' -> {
                double radiusX = argument(true);
                double radiusY = argument(false);
                double rotationDegrees = argument(false);
                // The standard makes this one separator mandatory, and it is the only one that is:
                // without it the rotation's digits would swallow the flag that follows them.
                requiredSeparator();
                boolean largeArc = flag();
                skipCommaWhitespace();
                boolean sweep = flag();
                float x = (float) (originX + argument(false));
                float y = (float) (originY + argument(false));
                ellipticArc(radiusX, radiusY, rotationDegrees, largeArc, sweep, x, y);
            }
            default -> throw new IllegalStateException("unhandled path command " + command);
        }
        previous = command;
    }

    private void openSubpath() {
        if (!open) {
            builder.moveTo(subpathStartX, subpathStartY);
            open = true;
            currentX = subpathStartX;
            currentY = subpathStartY;
        }
    }

    private void lineTo(float x, float y) {
        builder.lineTo(x, y);
        currentX = x;
        currentY = y;
    }

    private void cubicTo(float c1x, float c1y, float c2x, float c2y, float x, float y) {
        builder.curveTo(c1x, c1y, c2x, c2y, x, y);
        controlX = c2x;
        controlY = c2y;
        currentX = x;
        currentY = y;
    }

    private void quadraticTo(float cx, float cy, float x, float y) {
        builder.quadTo(cx, cy, x, y);
        controlX = cx;
        controlY = cy;
        currentX = x;
        currentY = y;
    }

    private void ellipticArc(double radiusX, double radiusY, double rotationDegrees,
                             boolean largeArc, boolean sweep, float x, float y) {
        if (x == currentX && y == currentY) {
            // The standard omits the segment entirely: with coincident endpoints there is no arc to
            // pick, and joining them with a line would draw an edge nothing authored.
            return;
        }
        double rx = Math.abs(radiusX);
        double ry = Math.abs(radiusY);
        // Zero at the geometry's own resolution, not merely at zero: a radius that narrows to a
        // float zero cannot describe an ellipse the points could sit on, and its square underflows
        // to zero in the conversion below, which would divide by it. The standard's rule for a zero
        // radius — draw the straight line — is the right answer for both.
        if ((float) rx == 0f || (float) ry == 0f) {
            lineTo(x, y);
            return;
        }
        double rotation = Math.toRadians(rotationDegrees);
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double halfDx = (currentX - (double) x) * 0.5;
        double halfDy = (currentY - (double) y) * 0.5;
        double primeX = cos * halfDx + sin * halfDy;
        double primeY = -sin * halfDx + cos * halfDy;
        double overshoot = primeX * primeX / (rx * rx) + primeY * primeY / (ry * ry);
        if (overshoot > 1.0) {
            // Radii too small to reach across the endpoints are scaled up until they exactly do,
            // rather than refused: the standard makes this a correction, not an error.
            double scale = Math.sqrt(overshoot);
            rx *= scale;
            ry *= scale;
        }
        double rx2 = rx * rx;
        double ry2 = ry * ry;
        double numerator = rx2 * ry2 - rx2 * primeY * primeY - ry2 * primeX * primeX;
        // The denominator cannot be zero: it vanishes only for coincident endpoints, which returned
        // above. The numerator is non-negative in exact arithmetic once the scale-up has run, so the
        // clamp is against rounding at the tangent case and nothing else — without it the square
        // root of a tiny negative would put a NaN centre into the geometry.
        double denominator = rx2 * primeY * primeY + ry2 * primeX * primeX;
        double reach = Math.sqrt(Math.max(0.0, numerator / denominator));
        if (largeArc == sweep) {
            reach = -reach;
        }
        double centrePrimeX = reach * rx * primeY / ry;
        double centrePrimeY = -reach * ry * primeX / rx;
        double centreX = cos * centrePrimeX - sin * centrePrimeY + (currentX + (double) x) * 0.5;
        double centreY = sin * centrePrimeX + cos * centrePrimeY + (currentY + (double) y) * 0.5;
        double startUnitX = (primeX - centrePrimeX) / rx;
        double startUnitY = (primeY - centrePrimeY) / ry;
        double endUnitX = (-primeX - centrePrimeX) / rx;
        double endUnitY = (-primeY - centrePrimeY) / ry;
        // The two parameters come from atan2 and are differenced, rather than from the acos of a
        // normalised dot product. An acos loses precision as the square of the angle between the
        // vectors, so a nearly complete arc — whose two parameters are nearly equal — collapses to
        // a swept angle of zero while the endpoints are still distinct, and a full circle of a
        // large radius would silently become a line.
        double startAngle = Math.atan2(startUnitY, startUnitX);
        double sweepAngle = Math.atan2(endUnitY, endUnitX) - startAngle;
        if (!sweep && sweepAngle > 0.0) {
            sweepAngle -= Math.PI * 2.0;
        } else if (sweep && sweepAngle < 0.0) {
            sweepAngle += Math.PI * 2.0;
        }
        if (sweepAngle == 0.0) {
            // Endpoints distinct, but so close against these radii that the swept angle rounds away
            // entirely. The arc between them is a straight nothing, and drawing it as one is what
            // the two degeneracies above already do.
            lineTo(x, y);
            return;
        }
        appendArc(centreX, centreY, rx, ry, rotation, startAngle, sweepAngle, x, y);
        currentX = x;
        currentY = y;
    }

    /** Short cubic arc reduction; final endpoint stays the authored one. */
    private void appendArc(double cx, double cy, double rx, double ry,
                           double rotation, double start, double sweep, float endX, float endY) {
        int pieces = (int) Math.ceil(Math.abs(sweep) / (Math.PI / 4));
        double step = sweep / pieces;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        for (int i = 0; i < pieces; i++) {
            double a = start + step * i;
            double b = a + step;
            double k = 4.0 / 3.0 * Math.tan(step / 4.0);
            double c1x = rx * (Math.cos(a) - k * Math.sin(a));
            double c1y = ry * (Math.sin(a) + k * Math.cos(a));
            double c2x = rx * (Math.cos(b) + k * Math.sin(b));
            double c2y = ry * (Math.sin(b) - k * Math.cos(b));
            double px = rx * Math.cos(b);
            double py = ry * Math.sin(b);
            builder.curveTo(cx + cos * c1x - sin * c1y, cy + sin * c1x + cos * c1y,
                    cx + cos * c2x - sin * c2y, cy + sin * c2x + cos * c2y,
                    i == pieces - 1 ? endX : cx + cos * px - sin * py,
                    i == pieces - 1 ? endY : cy + sin * px + cos * py);
        }
    }

    // ---------------------------------------------------------------------- the grammar's tokens

    private double argument(boolean firstInSet) {
        if (!firstInSet) {
            skipCommaWhitespace();
        }
        return number();
    }

    private double number() {
        int start = at;
        if (at < data.length() && (data.charAt(at) == '+' || data.charAt(at) == '-')) {
            at++;
        }
        int whole = digits();
        int fraction = 0;
        if (at < data.length() && data.charAt(at) == '.') {
            at++;
            fraction = digits();
        }
        if (whole == 0 && fraction == 0) {
            at = start;
            throw error("a number");
        }
        if (at < data.length() && (data.charAt(at) == 'e' || data.charAt(at) == 'E')) {
            // An exponent is optional, so an 'e' with no digits after it is not a malformed number —
            // it is the end of this one, and whatever follows is read on its own terms.
            int beforeExponent = at;
            at++;
            if (at < data.length() && (data.charAt(at) == '+' || data.charAt(at) == '-')) {
                at++;
            }
            if (digits() == 0) {
                at = beforeExponent;
            }
        }
        double value = Double.parseDouble(data.substring(start, at));
        if (!Float.isFinite((float) value)) {
            throw new IllegalArgumentException("SVG path data at offset " + start
                    + ": expected a number a coordinate can hold, found '"
                    + data.substring(start, at) + "'");
        }
        return value;
    }

    private int digits() {
        int start = at;
        while (at < data.length() && data.charAt(at) >= '0' && data.charAt(at) <= '9') {
            at++;
        }
        return at - start;
    }

    private boolean flag() {
        if (at < data.length()) {
            char c = data.charAt(at);
            if (c == '0' || c == '1') {
                at++;
                return c == '1';
            }
        }
        throw error("an arc flag, 0 or 1");
    }

    private void skipWhitespace() {
        while (at < data.length() && isWhitespace(data.charAt(at))) {
            at++;
        }
    }

    private void skipCommaWhitespace() {
        skipWhitespace();
        if (at < data.length() && data.charAt(at) == ',') {
            at++;
            skipWhitespace();
        }
    }

    private void requiredSeparator() {
        int before = at;
        skipCommaWhitespace();
        if (at == before) {
            throw error("a separator before the arc flags");
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private IllegalArgumentException error(String expected) {
        String found = at < data.length() ? "'" + data.charAt(at) + "'" : "end of input";
        return new IllegalArgumentException(
                "SVG path data at offset " + at + ": expected " + expected + ", found " + found);
    }
}
