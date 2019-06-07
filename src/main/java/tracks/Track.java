package tracks;

import java.util.ArrayList;
import java.util.Iterator;

import graph.Node;

public class Track implements Iterable<Node> {
	ArrayList<Tracklet> tracklets;

	public Track() {
		tracklets = new ArrayList<Tracklet>();
	}
	
	@Override
	public Iterator<Node> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

}
