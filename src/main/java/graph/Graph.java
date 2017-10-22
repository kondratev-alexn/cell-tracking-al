package graph;

import java.util.ArrayList;

/* let it be connected undirected graph (should be sufficient for trees) */
public class Graph {
	private ArrayList<Node> nodes; // vertices of the graph
	private ArrayList<Arc> arcs; // edges of the graph
	
//TODO when adding arcs add the information about it in nodes or in adj matrix to use the graph easily
	ArrayList<ArrayList<Integer>> adjLists;
	
	/* plain add node intor set of nodes */
	public void addNode(Node v) {
		nodes.add(v);
	}
	
	public void addNodeByTimeAndIndex(int time, int index) {
		Node v = new Node(time, index);
		addNode(v);
	}

	public void addArc(Arc arc) {
		arcs.add(arc);
	}

	/* adds the arc only if both nodes are in the graph */
	public void addArcFromTo(Node from, Node to) {
		if (isNodeInGraph(from) && isNodeInGraph(to)) {
			Arc arc = new Arc(from, to);
			arcs.add(arc);
		}
	}
	
	public void addNodeToNode(Node nodeInGraph, Node newNode) {
		nodes.add(newNode);
		addArcFromTo(nodeInGraph, newNode);
	}

	/* adds the arc. If the nodes are not in the graph, adds them first */
	public void addArcFromToAddable(Node from, Node to) {
		if (!isNodeInGraph(from))
			addNode(from);
		if (!isNodeInGraph(to))
			addNode(to);
		Arc arc = new Arc(from, to);
		arcs.add(arc);
	}

	private boolean isNodeInGraph(Node v) {
		if (nodes.contains(v))
			return true;
		return false;
	}
}
