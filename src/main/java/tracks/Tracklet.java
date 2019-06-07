package tracks;

import java.util.ArrayList;
import java.util.Iterator;

import graph.Node;

/**
 * Class representing a sub-track of components from consequent slices
 *
 */
public class Tracklet implements Iterable<Node> {
	private ArrayList<Node> nodes;
	private Tracklet nextTracklet;
	
	public Tracklet() {
		nodes = new ArrayList<Node>(5);	
		nextTracklet = null;
	}
	
	public Node addNode(int slice, int index) {
		return addNode(new Node(slice, index));
	}
	
	public Node addNode(Node node) {
		if (!canAdd(node.get_t()))
			return null;
		nodes.add(node);
		return node;
	}
	
	public Tracklet nextTracklet() {
		return nextTracklet;
	}
	
	public int startSlice() {
		return nodes.get(0).get_t();
	}
	
	public int length() {
		return nodes.size();
	}
	
	private boolean canAdd(int slice) { 
		if (nodes.isEmpty())
			return true;
		int prevSlice = nodes.get(nodes.size()-1).get_t();
		if (prevSlice == slice-1)
			return true;
		return false;
	}

	@Override
	public Iterator<Node> iterator() {
		return nodes.iterator();
	}
}
