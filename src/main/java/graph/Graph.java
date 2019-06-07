package graph;

import java.util.ArrayList;

/* let it be connected undirected graph (should be sufficient for trees) */
public class Graph {
	protected ArrayList<Node> nodes; // vertices of the graph
	protected ArrayList<Arc> arcs; // edges of the graph

	// to use the graph easily
	protected ArrayList<ArrayList<Integer>> adjLists; // has the same size as "nodes"

	public Graph(int nNodes, int nArcs, int nAdj) {
		nodes = new ArrayList<Node>(5);
		arcs = new ArrayList<Arc>(5);
		adjLists = new ArrayList<ArrayList<Integer>>(nAdj);
	}

	/* "atomic" add node into set of nodes */
	public void addNode(Node v) {
		nodes.add(v);
		adjLists.add(new ArrayList<Integer>());
	}

	public void addNodeByTimeAndIndex(int time, int index) {
		Node v = new Node(time, index);
		addNode(v);
	}

	public void addArc(Arc arc) {
		arcs.add(arc);
		int i = nodes.indexOf(arc.getFromNode());
		int j = nodes.indexOf(arc.getToNode());
		adjLists.get(i).add(j);
		//System.out.println(" ### Graph: Arc from " + i + " to " + j + " added");
	}

	public int getNodeIndexByGlobalIndex(int nodeIndex) {
		return nodes.get(nodeIndex).get_i();
	}

	public int getNodeSliceByGlobalIndex(int nodeIndex) {
		return nodes.get(nodeIndex).get_t();
	}

	// return -1 if child is empty
	public int getFirstChildByGlobalIndex(int adjIndex) {
		ArrayList<Integer> childs = adjLists.get(adjIndex);
		return childs.isEmpty() ? -1 : childs.get(0);
	}

	public ArrayList<ArrayList<Integer>> getAdjList() {
		return adjLists;
	}

	/* adds the arc only if both nodes are in the graph */
	public void addArcFromTo(Node from, Node to) {
		if (isNodeInGraph(from) && isNodeInGraph(to)) {
			Arc arc = new Arc(from, to);
			addArc(arc);
		}
	}

	public void addNodeToNode(Node nodeInGraph, Node newNode) {
		nodes.add(newNode);
		addArcFromTo(nodeInGraph, newNode);
	}

	/*
	 * adds the arc. If the nodes are not in the graph, adds them first, else find
	 * existing nodes and create arc
	 */
	public void addArcFromToAddable(Node from, Node to) {
		if (!isNodeInGraph(from))
			addNode(from);
		else
			from = findNode(from);
		if (!isNodeInGraph(to))
			addNode(to);
		else
			to = findNode(to);
		Arc arc = new Arc(from, to);
		addArc(arc);
	}

	/*
	 * add arc from node with global index 'nodeIndex' to Node v (which will be
	 * added if not in the graph Returns index of the node 'to'
	 */
	public void addArcFromIndexToNodeAddable(int fromNodeIndex, Node to) {
		if (fromNodeIndex < 0 || fromNodeIndex > nodes.size())
			return;
		if (!isNodeInGraph(to))
			addNode(to);
		else
			to = findNode(to);
		Node from = nodes.get(fromNodeIndex);
		Arc arc = new Arc(from, to);
		addArc(arc);
	}

	public void addArcFromIndexToIndexAddable(int fromNodeIndex, int toNodeIndex) {
		if (fromNodeIndex < 0 || fromNodeIndex > nodes.size() || toNodeIndex < 0 || toNodeIndex > nodes.size())
			return;
		Node from = nodes.get(fromNodeIndex);
		Node to = nodes.get(toNodeIndex);
		Arc arc = new Arc(from, to);
		addArc(arc);
	}

	private boolean isNodeInGraph(Node v) {
		if (nodes.contains(v))
			return true;
		return false;
	}

	public int getNodeIndex(Node v) {
		return nodes.indexOf(v);
	}

	public static ArrayList<ArrayList<Integer>> copyAdjList(ArrayList<ArrayList<Integer>> list) {
		ArrayList<ArrayList<Integer>> result = new ArrayList<ArrayList<Integer>>(list.size());
		ArrayList<Integer> currlist;
		for (int i = 0; i < list.size(); i++) {
			currlist = new ArrayList<Integer>(list.get(i).size()); // result.add(new ArrayList())
			for (int j = 0; j < list.get(i).size(); j++) {
				currlist.add(list.get(i).get(j));
			}
			result.add(currlist);
		}
		return result;
	}

	/*
	 * returns the index in adj list which leads to the %index%, its "grandparent"
	 */
	public int getStartingAdjIndex(int index) {
		return getStartingAdjIndex(adjLists, index);
	}

	public static int getStartingAdjIndex(ArrayList<ArrayList<Integer>> adj, int index) {
		ArrayList<Integer> childs = null;
		for (int i = 0; i < adj.size(); i++) {
			childs = adj.get(i);
			if (childs.contains(index))
				return getStartingAdjIndex(adj, i);
		}
		return index;
	}

	public boolean checkNoEqualNodes() {
		for (int i = 0; i < nodes.size(); i++) {
			for (int j = 0; j < nodes.size(); j++) {
				Node vi = nodes.get(i);
				Node vj = nodes.get(j);
				if (i != j && vi.equals(vj)) {
					System.out.println("found equals node, indexes are " + i + ", " + j);
					return false;
				}
			}
		}
		return true;
	}

	// If the node exists in the graph, return the existing node, otherwise return
	// the parameter
	private Node findNode(Node v) {
		for (int i = 0; i < nodes.size(); i++) {
			if (v.equals(nodes.get(i)))
				return nodes.get(i);
		}
		return v;
	}

	/* removes childs from adj index */
	private void clearChildsByAdjIndex(int adjIndex) {
		adjLists.get(adjIndex).clear();
	}
	
	/** Return length of the path starting from adjIndex to childs != 1 
	 * 
	 * @param adjIndex
	 * @return
	 */
	public static int pathLength(ArrayList<ArrayList<Integer>> adj, int adjIndex) {
		int length = 1;
		ArrayList<Integer> childs = adj.get(adjIndex);
		while (childs.size() == 1) {
			++length;
			childs = adj.get(childs.get(0));
		}
		System.out.format("track length is %d %n ", length);
		return length;
	}
	
	public static void removePath(ArrayList<ArrayList<Integer>> adj, int adjIndex) {
		int startAdjIndex = getStartingAdjIndex(adj, adjIndex);
		ArrayList<Integer> nextChilds = adj.get(startAdjIndex);
		ArrayList<Integer> prevChilds;
		while (nextChilds.size() == 1) {
			prevChilds = nextChilds;
			nextChilds = adj.get(nextChilds.get(0));
			prevChilds.clear();
		}
	}

	/* remove arcs from node with given adj index. 
	 * Also removes its childs from adj list */
	public void removeArcAndChildsByAdjIndex(int fromAdjIndex) {
		Node to, from;
		for (int i = arcs.size() - 1; i >= 0; i--) {
			from = arcs.get(i).getFromNode();
			to = arcs.get(i).getToNode();
			if (from == nodes.get(fromAdjIndex))
				arcs.remove(i);
		}
		
		clearChildsByAdjIndex(fromAdjIndex);
	}

	/* return list of arcs which nodes' time is <= t */
	public ArrayList<Arc> getArcsBeforeTimeSlice(int t) {
		ArrayList<Arc> result = new ArrayList<Arc>(arcs.size() / 10);
		for (int i = 0; i < arcs.size(); i++) {
			Arc a = arcs.get(i);
			if (a.getToNode().get_t() <= t && a.getFromNode().get_t() <= t)
				result.add(a);
		}
		return result;
	}

	public ArrayList<Arc> getArcs() {
		return arcs;
	}

	private String adjListsToString() {
		String result = "[";
		ArrayList<Integer> adj;
		for (int i = 0; i < adjLists.size(); i++) {
			adj = adjLists.get(i);
			result = result.concat("[" + i + " | ");
			for (int j = 0; j < adj.size(); j++) {
				result = result.concat(adj.get(j).toString());
				if (j < adj.size() - 1)
					result = result.concat(", ");
			}
			result = result.concat("]");
			if (i % 10 - 9 == 0)
				result = result.concat(System.getProperty("line.separator"));
		}
		result = result.concat("]");
		return result;
	}

	@Override
	public String toString() {
		return "Graph [nodes=" + nodes + " \n arcs=" + arcs + " \n adjLists=" + adjListsToString() + "]";
	}
}
