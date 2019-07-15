package tracks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import tracks.TrackAdj;
import graph.Graph;

public class TracksAdj {
	// list of tracks
	private ArrayList<TrackAdj> tracks;
	// graph corresponding to tracks
	private Graph graph;

	public TracksAdj(Graph gr, int minTrackLength) throws Exception {
		graph = gr;
		tracks = new ArrayList<TrackAdj>();

		fillTracks(gr, minTrackLength);
		sortTrackByLastSlice();
	}

	public int tracksCount() {
		return tracks.size();
	}

	public TrackAdj getTrack(int index) {
		if (index < 0 || index >= tracks.size())
			return null;
		return tracks.get(index);
	}
	
	public void sortTrackByLastSlice() {
		ArrayList<Integer> endSlices = new ArrayList<Integer>(tracksCount());
		for (int i=0; i<tracksCount(); ++i) {
			TrackAdj tr = tracks.get(i);
			int endSlice = tr.getLastSlice();
			endSlices.add(endSlice);
		}
		for (int i=0; i<tracksCount() - 1; ++i) {
			int endSliceI = endSlices.get(i);
			for (int j=i+1; j<tracksCount(); ++j) {
				int endSliceJ = endSlices.get(j);
				if (endSliceI > endSliceJ) {
					Collections.swap(endSlices, i, j);
					Collections.swap(tracks, i, j);
				}
			}
		}
		
		// check sorting
		for (int i=0; i<tracksCount(); ++i) {
			TrackAdj tr = tracks.get(i);
			int endSlice = tr.getLastSlice();
			System.out.format("%d ", endSlice);
		}
		System.out.println();
	}

	// fills array of tracks. Graph must not have any divisions by that moment
	void fillTracks(Graph gr, int minTrackLength) throws Exception {
		ArrayList<ArrayList<Integer>> adj = Graph.copyAdjList(gr.getAdjList());
		System.out.println(adj.toString());
		ArrayList<Integer> childs;
		ArrayList<Boolean> seenAdj = new ArrayList<Boolean>(adj.size());
		for (int i = 0; i < adj.size(); i++)
			seenAdj.add(false);
		// System.out.println(seenAdj.toString());
		TrackAdj tr;
		int trackLength;
		int startIndex = -1, childIndex = -1;

		for (int i = 0; i < adj.size(); i++) {
			startIndex = Graph.getStartingAdjIndex(adj, i);
			// System.out.println(startIndex);
			if (seenAdj.get(startIndex))
				continue;

			trackLength = 1;
			childs = adj.get(startIndex);
			childIndex = startIndex; // childIndex has also the meaning of the last index
			seenAdj.set(startIndex, true);

			while (!childs.isEmpty()) {
				if (childs.size() > 1) {
					throw new Exception(
							"Child size > 1 occured where it shouldn't be. Maybe you are using this method in the wrong place?");
				}
				childIndex = childs.get(0);
				childs = adj.get(childIndex);
				seenAdj.set(childIndex, true);
				++trackLength;
			}

			tr = new TrackAdj(startIndex, childIndex, gr.getNodeSliceByGlobalIndex(startIndex),
					gr.getNodeSliceByGlobalIndex(childIndex), gr.getNodeIndexByGlobalIndex(startIndex), trackLength);
			if (trackLength > minTrackLength) {
				tracks.add(tr);
			}
		}
	}

	public int getStartAdjIndexForTrack(int trackIndex) {
		return tracks.get(trackIndex).getStartAdjIndex();
	}

	public int getEndAdjIndexForTrack(int trackIndex) {
		return tracks.get(trackIndex).getEndAdjIndex();
	}

	public int getStartSliceForTrack(int trackIndex) {
		return tracks.get(trackIndex).getFirstSlice();
	}
	
	public int getLastSliceForTrack(int trackIndex) {
		return tracks.get(trackIndex).getLastSlice();
	}
	public int getFirstComponentIndexForTrack(int trackIndex) {
		return tracks.get(trackIndex).getFirstComponentIndex();
	}

	public void setTrackAsWhiteBlobParent(int trackIndex) {
		tracks.get(trackIndex).setAsWhiteBlobParent();
	}

	public boolean isTrackWhiteBlobParent(int trackIndex) {
		return tracks.get(trackIndex).isWhiteBlobParent();
	}

	public int getLength(int trackIndex) {
		return tracks.get(trackIndex).getLength();
	}

	/* removes first component from track */
	private void disconnectFirstComponentFromTrack(int trackIndex) {
		TrackAdj tr = tracks.get(trackIndex);
		if (tr.getStartAdjIndex() == tr.getEndAdjIndex()) {
			System.out.println("trying to remove first component from track consisting only of 1 component");
		}
		int adjIndex = tr.getStartAdjIndex();

		ArrayList<ArrayList<Integer>> adj = graph.getAdjList();
		ArrayList<Integer> childs = adj.get(adjIndex);
		if (childs.isEmpty()) {
			System.out.println("trying to remove component with no childs from track");
			return;
		}

		int newStartAdjIndex = childs.get(0);
		int newFirstSlice = graph.getNodeSliceByGlobalIndex(newStartAdjIndex);
		int newFirstComponentIndex = graph.getNodeIndexByGlobalIndex(newStartAdjIndex);

		tr.changeValuesOnFirstComponentRemoval(newFirstSlice, newFirstComponentIndex, newStartAdjIndex);
		tr.decreaseLength(1);
		graph.removeArcAndChildsByAdjIndex(adjIndex); // remove from graph
	}

	/* disconnects first componentsCount components from track and from the graph */
	public boolean disconnectFirstComponentsFromTrack(int trackIndex, int componentsCount) {
		TrackAdj tr = tracks.get(trackIndex);
		if (tr.getLength() <= componentsCount) {
			//trying to delete the whole track - better return false and do something
			return false;
		}
		for (int i = 0; i < componentsCount; i++) {
			disconnectFirstComponentFromTrack(trackIndex);
		}
		return true;
	}

	public ArrayList<Integer> getTrackIndexesListStartingInSlice(int startSlice) {
		ArrayList<Integer> list = new ArrayList<Integer>(1);
		for (int i = 0; i < tracksCount(); i++) {
			if (getStartSliceForTrack(i) == startSlice)
				list.add(i);
		}
		return list;
	}

	public void printTracksInfo() {
		for (int i = 0; i < tracks.size(); i++) {
			System.out.println(tracks.get(i));
		}
	}

}
