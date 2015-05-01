package com.itextpdf.text.pdf.parser.clipper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class ClipperOffset {
    private List<List<IntPoint>> m_destPolys;
    private List<IntPoint> m_srcPoly;
    private List<IntPoint> m_destPoly;
    private List<DoublePoint> m_normals = new ArrayList<DoublePoint>();
    private double m_delta, m_sinA, m_sin, m_cos;
    private double m_miterLim, m_StepsPerRad;

    private IntPoint m_lowest;
    private PolyNode m_polyNodes = new PolyNode();
    private double ArcTolerance = 0.25;
    private double MiterLimit = 2.0;

    private static double two_pi = Math.PI * 2;
    private static final double def_arc_tolerance = 0.25;

    public double getArcTolerance() {
        return ArcTolerance;
    }

    public void setArcTolerance(double arcTolerance) {
        ArcTolerance = arcTolerance;
    }

    public double getMiterLimit() {
        return MiterLimit;
    }

    public void setMiterLimit(double miterLimit) {
        MiterLimit = miterLimit;
    }

    public ClipperOffset(double miterLimit, double arcTolerance) {
        MiterLimit = miterLimit;
        ArcTolerance = arcTolerance;
        m_lowest.X = -1;
    }
    //------------------------------------------------------------------------------

    public void Clear() {
        m_polyNodes.getChilds().clear();
        m_lowest.X = -1;
    }
    //------------------------------------------------------------------------------


    static int Round(double value) {
        return value < 0 ? (int) (value - 0.5) : (int) (value + 0.5);
    }
    //------------------------------------------------------------------------------

    public void AddPath(List<IntPoint> path, JoinType joinType, EndType endType) {
        int highI = path.size() - 1;
        if (highI < 0)
            return;
        PolyNode newNode = new PolyNode();
        newNode.setJointype(joinType);
        newNode.setEndtype(endType);

        //strip duplicate points from path and also get index to the lowest point ...
        if (endType == EndType.etClosedLine || endType == EndType.etClosedPolygon)
            while (highI > 0 && path.get(0).equals(path.get(highI)))
                highI--;
        //newNode.m_polygon.Capacity = highI + 1;
        newNode.polygon.add(path.get(0));
        int j = 0, k = 0;
        for (int i = 1; i <= highI; i++)
            //todo: check
            if (IntPoint.notEquals(newNode.polygon.get(j), path.get(i))) {
                j++;
                newNode.polygon.add(path.get(i));
                if (path.get(i).Y > newNode.polygon.get(k).Y ||
                        (path.get(i).Y == newNode.polygon.get(k).Y &&
                                path.get(i).X < newNode.polygon.get(k).X))
                    k = j;
            }
        if (endType == EndType.etClosedPolygon && j < 2)
            return;

        m_polyNodes.addChild(newNode);

        //if this path's lowest pt is lower than all the others then update m_lowest
        if (endType != EndType.etClosedPolygon)
            return;
        if (m_lowest.X < 0)
            m_lowest = new IntPoint(m_polyNodes.getChildCount() - 1, k);
        else {
            IntPoint ip = m_polyNodes.getChilds().get((int) m_lowest.X).polygon.get((int) m_lowest.Y);
            if (newNode.polygon.get(k).Y > ip.Y ||
                    (newNode.polygon.get(k).Y == ip.Y &&
                            newNode.polygon.get(k).X < ip.X))
                m_lowest = new IntPoint(m_polyNodes.getChildCount() - 1, k);
        }
    }
    //------------------------------------------------------------------------------

    public void AddPaths(List<List<IntPoint>> paths, JoinType joinType, EndType endType) {
        for (List<IntPoint> p : paths)
            AddPath(p, joinType, endType);
    }
    //------------------------------------------------------------------------------

    private void FixOrientations() {
        //fixup orientations of all closed paths if the orientation of the
        //closed path with the lowermost vertex is wrong ...
        if (m_lowest.X >= 0 && !Clipper.Orientation(m_polyNodes.getChilds().get((int) m_lowest.X).polygon)) {
            for (int i = 0; i < m_polyNodes.getChildCount(); i++) {
                PolyNode node = m_polyNodes.getChilds().get(i);
                if (node.getEndtype() == EndType.etClosedPolygon || (node.getEndtype() == EndType.etClosedLine &&
                        Clipper.Orientation(node.polygon)))
                    Collections.reverse(node.polygon);
            }
        } else {
            for (int i = 0; i < m_polyNodes.getChildCount(); i++) {
                PolyNode node = m_polyNodes.getChilds().get(i);
                if (node.getEndtype() == EndType.etClosedLine &&
                        !Clipper.Orientation(node.polygon))
                    Collections.reverse(node.polygon);
            }
        }
    }
    //------------------------------------------------------------------------------


    static DoublePoint GetUnitNormal(IntPoint pt1, IntPoint pt2) {
        double dx = (pt2.X - pt1.X);
        double dy = (pt2.Y - pt1.Y);
        if ((dx == 0) && (dy == 0))
            return new DoublePoint();

        double f = 1 * 1.0 / Math.sqrt(dx * dx + dy * dy);
        dx *= f;
        dy *= f;

        return new DoublePoint(dy, -dx);
    }
    //------------------------------------------------------------------------------

    private void DoOffset(double delta) {
        m_destPolys = new ArrayList<List<IntPoint>>();
        m_delta = delta;

        //if Zero offset, just copy any CLOSED polygons to m_p and return ...
        if (ClipperBase.near_zero(delta)) {
            //m_destPolys.Capacity = m_polyNodes.ChildCount;
            for (int i = 0; i < m_polyNodes.getChildCount(); i++) {
                PolyNode node = m_polyNodes.getChilds().get(i);
                if (node.getEndtype() == EndType.etClosedPolygon)
                    m_destPolys.add(node.polygon);
            }
            return;
        }

        //see offset_triginometry3.svg in the documentation folder ...
        if (MiterLimit != 0)
            m_miterLim = 2 / (MiterLimit * MiterLimit);

        double y;
        if (ArcTolerance <= 0.0)
            y = def_arc_tolerance;
        else if (ArcTolerance > Math.abs(delta) * def_arc_tolerance)
            y = Math.abs(delta) * def_arc_tolerance;
        else
            y = ArcTolerance;
        //see offset_triginometry2.svg in the documentation folder ...
        double steps = Math.PI / Math.acos(1 - y / Math.abs(delta));
        m_sin = Math.sin(two_pi / steps);
        m_cos = Math.cos(two_pi / steps);
        m_StepsPerRad = steps / two_pi;
        if (delta < 0.0)
            m_sin = -m_sin;

        //m_destPolys.Capacity = m_polyNodes.ChildCount * 2;
        for (int i = 0; i < m_polyNodes.getChildCount(); i++) {
            PolyNode node = m_polyNodes.getChilds().get(i);
            m_srcPoly = node.polygon;

            int len = m_srcPoly.size();

            if (len == 0 || (delta <= 0 && (len < 3 ||
                    node.getEndtype() != EndType.etClosedPolygon)))
                continue;

            m_destPoly = new ArrayList<IntPoint>();

            if (len == 1) {
                if (node.getJointype() == JoinType.jtRound) {
                    double X = 1.0, Y = 0.0;
                    for (int j = 1; j <= steps; j++) {
                        m_destPoly.add(new IntPoint(
                                Round(m_srcPoly.get(0).X + X * delta),
                                Round(m_srcPoly.get(0).Y + Y * delta)));
                        double X2 = X;
                        X = X * m_cos - m_sin * Y;
                        Y = X2 * m_sin + Y * m_cos;
                    }
                } else {
                    double X = -1.0, Y = -1.0;
                    for (int j = 0; j < 4; ++j) {
                        m_destPoly.add(new IntPoint(
                                Round(m_srcPoly.get(0).X + X * delta),
                                Round(m_srcPoly.get(0).Y + Y * delta)));
                        if (X < 0)
                            X = 1;
                        else if (Y < 0)
                            Y = 1;
                        else
                            X = -1;
                    }
                }
                m_destPolys.add(m_destPoly);
                continue;
            }

            //build m_normals ...
            m_normals.clear();
            //m_normals.Capacity = len;
            for (int j = 0; j < len - 1; j++)
                m_normals.add(GetUnitNormal(m_srcPoly.get(j), m_srcPoly.get(j + 1)));
            if (node.getEndtype() == EndType.etClosedLine ||
                    node.getEndtype() == EndType.etClosedPolygon)
                m_normals.add(GetUnitNormal(m_srcPoly.get(len - 1), m_srcPoly.get(0)));
            else
                m_normals.add(new DoublePoint(m_normals.get(len - 2)));

            if (node.getEndtype() == EndType.etClosedPolygon) {
                int k = len - 1;
                for (int j = 0; j < len; j++) {
                    k = OffsetPoint(j, k, node.getJointype());
                }
                m_destPolys.add(m_destPoly);
            } else if (node.getEndtype() == EndType.etClosedLine) {
                int k = len - 1;
                for (int j = 0; j < len; j++) {
                    k = OffsetPoint(j, k, node.getJointype());
                }
                m_destPolys.add(m_destPoly);
                m_destPoly = new ArrayList<IntPoint>();
                //re-build m_normals ...
                DoublePoint n = m_normals.get(len - 1);
                for (int j = len - 1; j > 0; j--) {
                    m_normals.set(j, new DoublePoint(-m_normals.get(j - 1).X, -m_normals.get(j - 1).Y));
                }
                m_normals.set(0, new DoublePoint(-n.X, -n.Y));

                k = 0;
                for (int j = len - 1; j >= 0; j--) {
                    k = OffsetPoint(j, k, node.getJointype());
                }
                m_destPolys.add(m_destPoly);
            } else {
                int k = 0;
                for (int j = 1; j < len - 1; ++j) {
                    k = OffsetPoint(j, k, node.getJointype());
                }

                IntPoint pt1;
                if (node.getEndtype() == EndType.etOpenButt) {
                    int j = len - 1;
                    pt1 = new IntPoint((int) Round(m_srcPoly.get(j).X + m_normals.get(j).X *
                            delta), (int) Round(m_srcPoly.get(j).Y + m_normals.get(j).Y * delta));
                    m_destPoly.add(pt1);
                    pt1 = new IntPoint((int) Round(m_srcPoly.get(j).X - m_normals.get(j).X *
                            delta), (int) Round(m_srcPoly.get(j).Y - m_normals.get(j).Y * delta));
                    m_destPoly.add(pt1);
                } else {
                    int j = len - 1;
                    k = len - 2;
                    m_sinA = 0;
                    m_normals.set(j, new DoublePoint(-m_normals.get(j).X, -m_normals.get(j).Y));
                    if (node.getEndtype() == EndType.etOpenSquare)
                        DoSquare(j, k);
                    else
                        DoRound(j, k);
                }

                //re-build m_normals ...
                for (int j = len - 1; j > 0; j--)
                    m_normals.set(j, new DoublePoint(-m_normals.get(j - 1).X, -m_normals.get(j - 1).Y));

                m_normals.set(0, new DoublePoint(-m_normals.get(1).X, -m_normals.get(1).Y));

                k = len - 1;
                for (int j = k - 1; j > 0; --j) {
                    k = OffsetPoint(j, k, node.getJointype());
                }

                if (node.getEndtype() == EndType.etOpenButt) {
                    pt1 = new IntPoint((int) Round(m_srcPoly.get(0).X - m_normals.get(0).X * delta),
                            (int) Round(m_srcPoly.get(0).Y - m_normals.get(0).Y * delta));
                    m_destPoly.add(pt1);
                    pt1 = new IntPoint((int) Round(m_srcPoly.get(0).X + m_normals.get(0).X * delta),
                            (int) Round(m_srcPoly.get(0).Y + m_normals.get(0).Y * delta));
                    m_destPoly.add(pt1);
                } else {
                    k = 1;
                    m_sinA = 0;
                    if (node.getEndtype() == EndType.etOpenSquare)
                        DoSquare(0, 1);
                    else
                        DoRound(0, 1);
                }
                m_destPolys.add(m_destPoly);
            }
        }
    }
    //------------------------------------------------------------------------------

    public void Execute(List<List<IntPoint>> solution, double delta) throws ClipperException {
        solution.clear();
        FixOrientations();
        DoOffset(delta);
        //now clean up 'corners' ...
        Clipper clpr = new Clipper();
        clpr.AddPaths(m_destPolys, PolyType.ptSubject, true);
        if (delta > 0) {
            clpr.Execute(ClipType.ctUnion, solution,
                    PolyFillType.pftPositive, PolyFillType.pftPositive);
        } else {
            IntRect r = Clipper.GetBounds(m_destPolys);
            List<IntPoint> outer = new ArrayList<IntPoint>(4);

            outer.add(new IntPoint(r.left - 10, r.bottom + 10));
            outer.add(new IntPoint(r.right + 10, r.bottom + 10));
            outer.add(new IntPoint(r.right + 10, r.top - 10));
            outer.add(new IntPoint(r.left - 10, r.top - 10));

            clpr.AddPath(outer, PolyType.ptSubject, true);
            clpr.setReverseSolution(true);
            clpr.Execute(ClipType.ctUnion, solution, PolyFillType.pftNegative, PolyFillType.pftNegative);
            if (solution.size() > 0)
                solution.remove(0);
        }
    }
    //------------------------------------------------------------------------------

    public void Execute(PolyTree solution, double delta) throws ClipperException {
        solution.Clear();
        FixOrientations();
        DoOffset(delta);

        //now clean up 'corners' ...
        Clipper clpr = new Clipper();
        clpr.AddPaths(m_destPolys, PolyType.ptSubject, true);
        if (delta > 0) {
            clpr.Execute(ClipType.ctUnion, solution,
                    PolyFillType.pftPositive, PolyFillType.pftPositive);
        } else {
            IntRect r = Clipper.GetBounds(m_destPolys);
            List<IntPoint> outer = new ArrayList<IntPoint>(4);

            outer.add(new IntPoint(r.left - 10, r.bottom + 10));
            outer.add(new IntPoint(r.right + 10, r.bottom + 10));
            outer.add(new IntPoint(r.right + 10, r.top - 10));
            outer.add(new IntPoint(r.left - 10, r.top - 10));

            clpr.AddPath(outer, PolyType.ptSubject, true);
            clpr.setReverseSolution(true);
            clpr.Execute(ClipType.ctUnion, solution, PolyFillType.pftNegative, PolyFillType.pftNegative);
            //remove the outer PolyNode rectangle ...
            if (solution.getChildCount() == 1 && solution.getChilds().get(0).getChildCount() > 0) {
                PolyNode outerNode = solution.getChilds().get(0);
                //solution.Childs.Capacity = outerNode.ChildCount;
                solution.getChilds().set(0, outerNode.getChilds().get(0));
                solution.getChilds().get(0).setParent(solution);
                for (int i = 1; i < outerNode.getChildCount(); i++)
                    solution.addChild(outerNode.getChilds().get(i));
            } else
                solution.Clear();
        }
    }
    //------------------------------------------------------------------------------

    int OffsetPoint(int j, int k, JoinType jointype) {
        //cross product ...
        m_sinA = (m_normals.get(k).X * m_normals.get(j).Y - m_normals.get(j).X * m_normals.get(k).Y);

        if (Math.abs(m_sinA * m_delta) < 1.0) {
            //dot product ...
            double cosA = (m_normals.get(k).X * m_normals.get(j).X + m_normals.get(j).Y * m_normals.get(k).Y);
            if (cosA > 0) // angle ==> 0 degrees
            {
                m_destPoly.add(new IntPoint(Round(m_srcPoly.get(j).X + m_normals.get(k).X * m_delta),
                        Round(m_srcPoly.get(j).Y + m_normals.get(k).Y * m_delta)));
                return k;
            }
            //else angle ==> 180 degrees
        } else if (m_sinA > 1.0)
            m_sinA = 1.0;
        else if (m_sinA < -1.0)
            m_sinA = -1.0;

        if (m_sinA * m_delta < 0) {
            m_destPoly.add(new IntPoint(Round(m_srcPoly.get(j).X + m_normals.get(k).X * m_delta),
                    Round(m_srcPoly.get(j).Y + m_normals.get(k).Y * m_delta)));
            m_destPoly.add(m_srcPoly.get(j));
            m_destPoly.add(new IntPoint(Round(m_srcPoly.get(j).X + m_normals.get(j).X * m_delta),
                    Round(m_srcPoly.get(j).Y + m_normals.get(j).Y * m_delta)));
        } else
            switch (jointype) {
                case jtMiter: {
                    double r = 1 + (m_normals.get(j).X * m_normals.get(k).X +
                            m_normals.get(j).Y * m_normals.get(k).Y);
                    if (r >= m_miterLim)
                        DoMiter(j, k, r);
                    else
                        DoSquare(j, k);
                    break;
                }
                case jtSquare:
                    DoSquare(j, k);
                    break;
                case jtRound:
                    DoRound(j, k);
                    break;
            }
        return j;
    }
    //------------------------------------------------------------------------------


    void DoSquare(int j, int k) {
        double dx = Math.tan(Math.atan2(m_sinA,
                m_normals.get(k).X * m_normals.get(j).X + m_normals.get(k).Y * m_normals.get(j).Y) / 4);
        m_destPoly.add(new IntPoint(
                Round(m_srcPoly.get(j).X + m_delta * (m_normals.get(k).X - m_normals.get(k).Y * dx)),
                Round(m_srcPoly.get(j).Y + m_delta * (m_normals.get(k).Y + m_normals.get(k).X * dx))));
        m_destPoly.add(new IntPoint(
                Round(m_srcPoly.get(j).X + m_delta * (m_normals.get(j).X + m_normals.get(j).Y * dx)),
                Round(m_srcPoly.get(j).Y + m_delta * (m_normals.get(j).Y - m_normals.get(j).X * dx))));
    }
    //------------------------------------------------------------------------------


    void DoMiter(int j, int k, double r) {
        double q = m_delta / r;
        m_destPoly.add(new IntPoint(Round(m_srcPoly.get(j).X + (m_normals.get(k).X + m_normals.get(j).X) * q),
                Round(m_srcPoly.get(j).Y + (m_normals.get(k).Y + m_normals.get(j).Y) * q)));
    }
    //------------------------------------------------------------------------------

    void DoRound(int j, int k) {
        double a = Math.atan2(m_sinA, m_normals.get(k).X * m_normals.get(j).X + m_normals.get(k).Y * m_normals.get(j).Y);
        int steps = Math.max((int) Round(m_StepsPerRad * Math.abs(a)), 1);

        double X = m_normals.get(k).X, Y = m_normals.get(k).Y, X2;
        for (int i = 0; i < steps; ++i) {
            m_destPoly.add(new IntPoint(
                    Round(m_srcPoly.get(j).X + X * m_delta),
                    Round(m_srcPoly.get(j).Y + Y * m_delta)));
            X2 = X;
            X = X * m_cos - m_sin * Y;
            Y = X2 * m_sin + Y * m_cos;
        }
        m_destPoly.add(new IntPoint(
                Round(m_srcPoly.get(j).X + m_normals.get(j).X * m_delta),
                Round(m_srcPoly.get(j).Y + m_normals.get(j).Y * m_delta)));
    }
    //------------------------------------------------------------------------------
}