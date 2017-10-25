package graph;

import java.util.ArrayList;

public class Node {
	/*
	 * the node should be determined by these two values: time slice number and
	 * index of detection in this time slice ~D_t,i
	 */
	private int t; // time slice of the node
	private int i; // index for detection in time slice
	
	public Node(int time, int index) {
		t = time;
		i = index;
	}
	
	public int get_i() {
		return i;
	}
	
	public int get_t() {
		return t;
	}

	// checks if this node equals to the other one
	@Override
	public boolean equals(Object other) {
		if (other == null)
			return false;
		if (other == this)
			return true;
		if (!(other instanceof Node))
			return false;
		Node otherMyClass = (Node) other;
		boolean res;
		res = (otherMyClass.i == i && otherMyClass.t == t);
		return res;
	}

	@Override
	public String toString() {
		return "Node [t=" + t + ", i=" + i + "]";
	}
}
