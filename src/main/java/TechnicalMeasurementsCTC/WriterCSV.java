package TechnicalMeasurementsCTC;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import com.opencsv.CSVWriter;

public class WriterCSV {
	
	private Path filePath;
	private CSVWriter writer;

	public WriterCSV(Path _filePath) {
		filePath = _filePath;		

		File file = new File(filePath.toString()); 
	    try { 
	        // create FileWriter object with file as parameter 
	        FileWriter outputfile = new FileWriter(file); 
	  
	        // create CSVWriter object file writer object as parameter 
	        writer = new CSVWriter(outputfile); 
	    } 
	    catch (IOException e) {
	        // TODO Auto-generated catch block 
	        e.printStackTrace(); 
	    } 
	}
	
	public void writeLine(String[] line) {
	        writer.writeNext(line);
	}
	
	public void closeWriter() {
		try {
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
