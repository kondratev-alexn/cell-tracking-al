package graph;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;

public class MitosisInfo implements Serializable {
	private HashMap<Integer, TrackMitosisInfo> infos;

	public MitosisInfo() {
		infos = new HashMap<Integer, TrackMitosisInfo>();
	}

	public void addMitosisInfo(int trackIndex, int mitosisStartSlice, int mitosisEndSlice) {
		TrackMitosisInfo info = new TrackMitosisInfo(trackIndex, mitosisStartSlice, mitosisEndSlice);
		System.out.format("%n trying to put info: track %d start %d end %d %n", trackIndex, mitosisStartSlice,
				mitosisEndSlice);
		infos.put(trackIndex, info);
	}

	public boolean contains(int trackIndex, int slice) {
		if (!infos.containsKey(trackIndex))
			return false;
		int start = infos.get(trackIndex).mitosisStartSlice;
		int end = infos.get(trackIndex).mitosisEndSlice;
		if (slice < start || start > end)
			return false;
		return true;
	}

	public static void SerializeMitosisInfo(String filename, MitosisInfo info) {
//		System.out.println("Mitosis info: \n");
//		System.out.println(info.toString());
//		System.out.println();
		try {
			FileOutputStream fileOut = new FileOutputStream(filename);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(info);
			out.close();
			fileOut.close();
			System.out.printf("Serialized data is saved in " + filename);
		} catch (IOException i) {
			i.printStackTrace();
		}
	}

	public static MitosisInfo DeserializeMitosisInfo(String filename) {
		MitosisInfo e = null;
		try {
			FileInputStream fileIn = new FileInputStream(filename);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			e = (MitosisInfo) in.readObject();
			in.close();
			fileIn.close();
		} catch (IOException i) {
			i.printStackTrace();
			return e;
		} catch (ClassNotFoundException c) {
			System.out.println("MitosisInfo class not found");
			c.printStackTrace();
			return e;
		}
		return e;
	}

	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		for (Integer i : infos.keySet()) {
			out.append(infos.get(i).toString());
			out.append('\n');
		}
		return out.toString();
	}
}
