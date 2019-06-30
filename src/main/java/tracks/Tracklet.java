package tracks;

import java.util.ArrayList;

import graph.Node;

/**
 * Class representing a sub-track of components from consequent slices
 *
 */
public class Tracklet extends ArrayList<Node> {

	private static final long serialVersionUID = 661926598391511906L;

	public Tracklet() {
	}
	
	@Override
	public boolean add(Node node) {
		if (!canAdd(node.get_t()))
			return false;
		add(node);
		return true;
	}
	
	public boolean add(int slice, int index) {
		return add(new Node(slice, index));
	}
	
	
	public int startSlice() {
		return get(0).get_t();
	} 	
	
	private boolean canAdd(int slice) { 
		if (isEmpty())
			return true;
		int prevSlice = this.get(size()-1).get_t();
		if (prevSlice == slice-1)
			return true;
		return false;
	}
	
}
