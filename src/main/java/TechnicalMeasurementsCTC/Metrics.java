package TechnicalMeasurementsCTC;

import java.nio.file.Path;

import org.scijava.ItemIO;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;
import org.scijava.plugin.Parameter;

import de.mpicbg.ulman.ctc.workers.TRA;
import de.mpicbg.ulman.ctc.workers.SEG;
import de.mpicbg.ulman.ctc.workers.DET;

public class Metrics {
	
	private double _SEG;
	private double _TRA;
	private double _DET;
	
	private LogService log;

	public Metrics() {
		_SEG = -1;
		_TRA = -1;
		_DET = -1;
		log = new StderrLogService();
	}
	
	public double SEG() {
		return _SEG;
	}	
	public double TRA() {
		return _TRA;
	}	
	public double DET() {
		return _DET;
	}
	
	public void calculateMetrics(Path GTFolderPath, Path resultPath) {
		String GTdir  = GTFolderPath.toString();
		String RESdir = resultPath.toString();
		
		// SEG
		try {
			final SEG seg = new SEG(log);
			seg.doLogReports = false;
			seg.noOfDigits = 3;
			_SEG = seg.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC SEG measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC SEG measure error: "+e.getMessage());
		}
		
		// TRA
		try {
			final TRA tra = new TRA(log);
			tra.doConsistencyCheck = true;
			tra.doLogReports = false;
			tra.noOfDigits = 3;
			_TRA = tra.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC TRA measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC TRA measure error: "+e.getMessage());
		}
		
		// DET
		try {
			final DET det = new DET(log);
			det.doConsistencyCheck = true;
			det.doLogReports = false;
			det.noOfDigits = 3;
			_DET = det.calculate(GTdir, RESdir);
		}
		catch (RuntimeException e) {
			log.error("CTC DET measure problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC DET measure error: "+e.getMessage());
		}			
		
		log.info(_SEG);
		log.info(_TRA);
		log.info(_DET);
	}
}
