package cellTracking;

import java.util.ArrayList;

import graph.Graph;

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
	 * finds nearest components in comp1-comp2 not further than 'radius' pixels. 
	 * (t1 and t2 refers to time points of comp1 and comp2 respectively)
	 */
	public void findNearestComponents(ImageComponentsAnalysis comp1, int t1, ImageComponentsAnalysis comp2, int t2, float radius) {
		float mx, my;
		ArrayList<float[]> comp2Centers;
		for (int i=0; i<comp1.getComponentsCount(); i++) {
			mx = comp1.getComponentMassCenterX(i);
			my = comp1.getComponentMassCenterY(i);
		}
	}
}
