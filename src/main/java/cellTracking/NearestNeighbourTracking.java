package cellTracking;

import java.util.ArrayList;

import graph.Graph;
import graph.Node;
import point.Point;

public class NearestNeighbourTracking {
	private Graph cellGraph;

	private int currSlice;
	private int slicesCount;

	/*
	 * List of components classes, containing image with labels and information
	 * about them. Should be formed during processing and then processed.
	 */
	private ArrayList<ImageComponentsAnalysis> componentsList;

	public NearestNeighbourTracking(int slicesCount) {
		this.slicesCount = slicesCount;
	}

	public void addComponentsAnalysis(ImageComponentsAnalysis comps) {
		componentsList.add(comps);
	}

	/*
	 * finds nearest components in comp1-comp2 not further than 'radius' pixels. (t1
	 * and t2 refers to time points of comp1 and comp2 respectively)
	 */
	public void findNearestComponents(ImageComponentsAnalysis comp1, int t1, ImageComponentsAnalysis comp2, int t2,
			double radius) {
		Point m1, m2;
		ArrayList<Point> comp2Centers = new ArrayList<Point>(slicesCount);
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			comp2Centers.add(comp2.getComponentMassCenter(i));
		}
		int closestIndex;
		Node v1, v2;
		for (int i = 0; i < comp1.getComponentsCount(); i++) {
			m1 = comp1.getComponentMassCenter(i);
			closestIndex = findClosestPointIndex(m1, comp2Centers, radius);
			if (closestIndex != -1) { // closest component found, remove from list, add to graph
				v1 = new Node(t1, i);
				v2 = new Node(t2, closestIndex);
				cellGraph.addArcFromToAddable(v1, v2);
				comp2Centers.remove(closestIndex);
			} else {
				// closest component for current component in comp1 was not found - may be
				// remove it from detection (it may not be correct detection)
			}
		}
	}

	private int findClosestPointIndex(Point p, ArrayList<Point> points, double radius) {
		int min_i = -1;
		double min_dist = Double.MAX_VALUE;
		double dist;
		for (int i = 0; i < points.size(); i++) {
			dist = Point.dist(p, points.get(i));
			if (dist < radius && dist < min_dist) {
				min_dist = dist;
				min_i = i;
			}
		}
		return min_i;
	}
}
