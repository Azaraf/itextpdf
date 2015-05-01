package com.itextpdf.text.pdf.parser;

import com.itextpdf.awt.geom.Point2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Bezier curve.
 *
 * @since 5.5.6
 */
public class BezierCurve implements Shape {

    /**
     * If the distance between a point and a line is less than
     * this constant, then we consider the point lies on the line.
     */
    public static double curveCollinearityEpsilon = 1.0e-30;

    /**
     * In the case when neither the line ((x1, y1), (x4, y4)) passes
     * through both (x2, y2) and (x3, y3) nor (x1, y1) = (x4, y4) we
     * use the square of the sum of the distances mentioned below in
     * compare to this field as the criterion of good approximation.
     *     1. The distance between the line and (x2, y2)
     *     2. The distance between the line and (x3, y3)
     */
    public static double distanceToleranceSquare = 0.025D;

    /**
     * The Manhattan distance is used in the case when either the line
     * ((x1, y1), (x4, y4)) passes through both (x2, y2) and (x3, y3)
     * or (x1, y1) = (x4, y4). The essential observation is that when
     * the curve is a uniform speed straight line from end to end, the
     * control points are evenly spaced from beginning to end. Our measure
     * of how far we deviate from that ideal uses distance of the middle
     * controls: point 2 should be halfway between points 1 and 3; point 3
     * should be halfway between points 2 and 4.
     */
    public static double distanceToleranceManhattan = 0.4D;

    private final List<Point2D> controlPoints;

    public BezierCurve(List<Point2D> controlPoints) {
        this.controlPoints = new ArrayList<Point2D>(controlPoints);
    }

    public List<Point2D> getBasePoints() {
        return controlPoints;
    }

    public List<Point2D> getPiecewiseLinearApproximation() {
        List<Point2D> points = new ArrayList<Point2D>();
        points.add(controlPoints.get(0));

        recursiveApproximation(controlPoints.get(0).getX(), controlPoints.get(0).getY(),
                               controlPoints.get(1).getX(), controlPoints.get(1).getY(),
                               controlPoints.get(2).getX(), controlPoints.get(2).getY(),
                               controlPoints.get(3).getX(), controlPoints.get(3).getY(), points);

        points.add(controlPoints.get(controlPoints.size() - 1));
        return points;
    }

    // Based on the De Casteljau's algorithm
    private void recursiveApproximation(double x1, double y1, double x2, double y2,
                                        double x3, double y3, double x4, double y4, List<Point2D> points) {
        // Subdivision using the De Casteljau's algorithm (t = 0.5)
        double x12 = (x1 + x2) / 2;
        double y12 = (y1 + y2) / 2;
        double x23 = (x2 + x3) / 2;
        double y23 = (y2 + y3) / 2;
        double x34 = (x3 + x4) / 2;
        double y34 = (y3 + y4) / 2;
        double x123 = (x12 + x23) / 2;
        double y123 = (y12 + y23) / 2;
        double x234 = (x23 + x34) / 2;
        double y234 = (y23 + y34) / 2;
        double x1234 = (x123 + x234) / 2;
        double y1234 = (y123 + y234) / 2;

        double dx = x4 - x1;
        double dy = y4 - y1;

        // Constructs the line passing through (x1, y1) and (x4, y4)
        // |Ax2 + By2 + C|, where Ax+By+C is the equation for the line mentioned above
        double d2 = Math.abs(((x2 - x4) * dy - (y2 - y4) * dx));

        // |Ax3 + Bx3 + C|
        double d3 = Math.abs(((x3 - x4) * dy - (y3 - y4) * dx));

        // True if neither the line passes through both (x2, y2) and (x3, y3)
        // nor (x1, y1) = (x4, y4)
        if (d2 > curveCollinearityEpsilon || d3 > curveCollinearityEpsilon) {
            // True if the square of the distance between (x2, y2) and the line plus
            // the distance between (x3, y3) and the line is lower than the tolerance square
            if ((d2 + d3) * (d2 + d3) <= distanceToleranceSquare * (dx * dx + dy * dy)) {
                points.add(new Point2D.Double(x1234, y1234));
                return;
            }

        } else {
            if ((Math.abs(x1 + x3 - x2 - x2) + Math.abs(y1 + y3 - y2 - y2) +
                    Math.abs(x2 + x4 - x3 - x3) + Math.abs(y2 + y4 - y3 - y3)) <= distanceToleranceManhattan) {
                points.add(new Point2D.Double(x1234, y1234));
                return;
            }
        }

        recursiveApproximation(x1, y1, x12, y12, x123, y123, x1234, y1234, points);
        recursiveApproximation(x1234, y1234, x234, y234, x34, y34, x4, y4, points);
    }
}
