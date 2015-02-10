package com.omr.app;
import helper.Filter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.MemoryUsage;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.lightcouch.CouchDbException;

import com.google.gson.JsonArray;
import com.omr.app.userinterface.MainFrame;
import com.omr.exceptions.AssessmentNotExist;
import com.omr.exceptions.CancelException;
import com.omr.exceptions.CellsWrongDetection;
import com.omr.exceptions.FileMovedFailed;
import com.omr.exceptions.MappingNotCorrect;
import com.omr.exceptions.QrFailedToDetect;
import com.omr.exceptions.UnableToDetectMarkers;
import com.omr.exceptions.UnableToDetectOptions;
import com.omr.exceptions.UnableToLoadImage;
import com.omr.exceptions.WrongFileAttributes;
import com.omr.exceptions.WrongMarkers;

import config.Config;
import couch.GetDocs;

public class OmrController extends Config{
	OmrModel sheet;
	MainFrame view;
	GetDocs docs;
	private Logger logger;
	private File[] filesInDirectory;
	File directory;
	private PrintStream outcsv;
	private SwingWorker<Void, String> worker;
	private String session;
	private FileHandler fh;
	private boolean LastUsedDirectory;

	public OmrController(FileHandler fh,MainFrame view, String session) {
		logger = Logger.getLogger(OmrController.class.getName());
		this.session = session;
		this.fh = fh;
		logger.addHandler(fh);
		logger.setUseParentHandlers(false);
		logger.log(Level.INFO, "Initializing Controller");
		this.view=view;
		try {
			outcsv = new PrintStream(new FileOutputStream(session+"/result.csv"));
		} catch (FileNotFoundException e) {
			logger.log(Level.SEVERE,"Can't Open CSV File For Resutls");
		}
	}
	public void refreshDialog()
	{
		view.getCancelButton().setEnabled(true);
		view.dialog.SheetNameLabel.setText("Sheet being decoded");
	}

	public boolean startApp(){
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				LastUsedDirectory=true;
				view.getBrowseButton().addActionListener(new ActionListener (){
					@Override
					public void actionPerformed(ActionEvent e) {

						JFileChooser c = new JFileChooser();
						c.setFileFilter(new FileNameExtensionFilter("Image Files", "png","jpg"));

						int rVal = c.showOpenDialog(new JPanel());
						if (rVal == JFileChooser.APPROVE_OPTION) {
							directory = c.getCurrentDirectory();
							filesInDirectory = c.getCurrentDirectory().listFiles(new Filter());
							LastUsedDirectory=false;
							view.setFolderPath(c.getCurrentDirectory().getAbsolutePath());
							if(filesInDirectory.length == 0){
								view.doNothing();
								return;
							}
							view.doCancel();
						}
						if (rVal == JFileChooser.CANCEL_OPTION) {
							view.doNothing();
						}
					}
				});
				view.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				view.setTitle("Omr Scanner");
				view.setVisible(true);
				view.getCancelButton().addActionListener(new ActionListener() {

					@Override
					public void actionPerformed(ActionEvent arg0) {
						view.getCancelButton().setEnabled(false);
						view.getStartScanButton().setEnabled(false);
						view.dialog.SheetNameLabel.setText("Operation being closed.Please wait");	
						worker.cancel(true);	
						

					}
				});
				view.dialog.addWindowListener(new WindowListener() {
					
					@Override
					public void windowOpened(WindowEvent arg0) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void windowIconified(WindowEvent arg0) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void windowDeiconified(WindowEvent arg0) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void windowDeactivated(WindowEvent arg0) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void windowClosing(WindowEvent arg0) {
						view.getStartScanButton().setEnabled(false);	
						JOptionPane.showMessageDialog(view, "Operation being closed please wait");
						worker.cancel(true);
						
					}
					
					@Override
					public void windowClosed(WindowEvent arg0) {
						
						
					}
					
					@Override
					public void windowActivated(WindowEvent arg0) {
						// TODO Auto-generated method stub
						
					}
				});
				view.dialog.getOkButton().addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent arg0) {
						
						view.dialog.setVisible(false);
					}
				});
				
				view.getStartScanButton().addActionListener(new ActionListener (){

					@Override
					public void actionPerformed(ActionEvent e) {
						view.updateConfig();

						view.showDialog();
						startscan();
						view.getStartScanButton().setEnabled(false);
						view.getBrowseButton().setEnabled(false);
					}});

			}
		});
		return false;

	}

	public void initDocs() throws MappingNotCorrect, CancelException, QrFailedToDetect{
		docs = new GetDocs();

		if(docs.readQRCode(sheet.getPath()+"/"+sheet.getfilename())){
			docs.Qrdone();
		}else if(view.isChecked()){
			logger.log(Level.WARNING,"Failed TO Read Qr Code");
			String id = null;
			id = view.getCodeFromUser(sheet.getPath()+"/"+sheet.getfilename());
			docs.Qrfailed(id);
		}else{
			throw new QrFailedToDetect(sheet.getfilename());
		}
		sheet.setQrCode(docs.getqrstr());
		sheet.setqrinfo(docs.getqrstr());
		sheet.setseq(docs.getMap());
		logger.log(Level.INFO, "Sheet Serial "+sheet.getQrCode());
	}

	public void initQuestions(int total,int[] optionscount, JsonArray tcols, JsonArray trows, int avgr) throws CellsWrongDetection {
		sheet.setQuestions(total);
		sheet.fillQuestions(optionscount);
	}
	public void genrslt(GetDocs docs,String name,String enu, String[] results) throws AssessmentNotExist{
		if(docs.subtst(enu,name,results,sheet.getQuestions())){
			logger.log(Level.INFO,"Subtest ready");
		}else{
			logger.log(Level.SEVERE,"DB Error ");
		}
	}

	public void startscan(){
		worker = new SwingWorker<Void, String>(){
			@Override
			protected Void doInBackground() throws Exception {

				view.SetTotalCounter(filesInDirectory.length);
				int index = 0;
				view.getCancelButton().setEnabled(true);
				view.dialog.getOkButton().setEnabled(false);
				for ( File file : filesInDirectory ) {

					if(isCancelled()){
						view.dialog.setVisible(false);
						view.getBrowseButton().setEnabled(true);
						view.getStartScanButton().setEnabled(true);
						refreshDialog();
						return null;
					}

					view.SetNumerator(++index);
					String curimgname = file.getName();
					logger.log(Level.INFO,"Selected file "+curimgname);

					try {

						sheet = new OmrModel(fh);
						setProgress(0);


						sheet.setpaths(curimgname, directory.toString());
						setProgress(5);

						sheet.init();
						setProgress(10);


						sheet.lookref("first");
						setProgress(20);
						sheet.scale();
						sheet.lookref("second");
						setProgress(25);
						initDocs();
						setProgress(30);
						
						sheet.circle(sheet.getQuestions());
						setProgress(40);

						initQuestions(sheet.getQuestions(),sheet.getoptions(),sheet.getcols(),sheet.getrows(),sheet.avgr());

						setProgress(50);

						String[] results = sheet.getresults();
						setProgress(60);
		
						outcsv.println(curimgname+","+Arrays.toString(results));
						setProgress(70);
						
						sheet.drawgrid();
						setProgress(80);

						genrslt(docs,sheet.getstudent(),"OMR", results);
						setProgress(90);
						
						sheet.ClearQuestions();

						//docs.push();
						setProgress(100);
						movefile(file.getPath(),0);
						
						System.out.println("Releasing");
						setProgress(100);
						sheet.release();
						publish(curimgname+"#"+sheet.getQrCode()+"#success");
					} catch (AssessmentNotExist|CellsWrongDetection | UnableToDetectOptions | WrongFileAttributes | UnableToLoadImage | UnableToDetectMarkers | MappingNotCorrect | WrongMarkers  e1 ){
						if(!(e1.getMessage().contains("Error Code10")&& LastUsedDirectory==false))
						{
						publish(curimgname+"#"+sheet.getQrCode()+"#"+e1.getMessage());
						e1.printStackTrace();
						sheet.release();
						movefile(file.getPath(),-1);
						}
						

					}
					
					catch (CouchDbException e2){
						publish(curimgname+"#"+sheet.getQrCode()+"#Error Code 13");
						movefile(file.getPath(),-1);
						sheet.release();
					} catch (RuntimeException e2){
						e2.printStackTrace();
						publish(curimgname+"#"+sheet.getQrCode()+"#Error Code 7");
						sheet.release();
						movefile(file.getPath(),-1);
					} catch (CancelException | QrFailedToDetect e1){
						publish(curimgname+"# #Error Code"+e1.getMessage());
						movefile(file.getPath(),1);
						sheet.release();
					}
					catch(OutOfMemoryError e4)
					{
						publish(curimgname+"#"+sheet.getQrCode()+"#"+"Our of Memory");
						e4.printStackTrace();	
						sheet.release();
						movefile(file.getPath(),-1);
					}
                    setProgress(100);
                    
                    

				}
				view.getStartScanButton().setEnabled(true);
                view.getBrowseButton().setEnabled(true);
                view.getCancelButton().setEnabled(false);
                view.dialog.getOkButton().setEnabled(true);
				return null;
			}
			@Override
			protected void process(List<String> chunks) {
				for(String line: chunks){

					String [] arg = line.split("#");
					display(arg[0],arg[1],arg[2]);
				}
			}
			@Override
			protected void done() {
				view.doCancel();
			}
		};
		worker.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				//System.out.println(evt);
				switch(evt.getPropertyName()){
				case "progress":
					view.SetProgressBarValue((int) evt.getNewValue());
					break;
				}

			}
		});
		worker.execute();
	}


	/*public void startscan(){
		worker = new SwingWorker<Void, String>(){
			@Override
			protected Void doInBackground() throws Exception {

				view.SetTotalCounter(filesInDirectory.length);
				int index = 0;
				for ( File file : filesInDirectory ) {

					if(isCancelled()){
						view.dialog.setVisible(false);

						return null;
					}

					view.SetNumerator(++index);
					String curimgname = file.getName();
					logger.log(Level.INFO,"Selected file "+curimgname);

					try {

						sheet = new OmrModel(fh);
						setProgress(0);


					    sheet.setpaths(curimgname, directory.toString());
						setProgress(5);

						sheet.init();
						setProgress(10);


						sheet.lookref("first");
						setProgress(20);
						sheet.scale();
						sheet.lookref("second");
						setProgress(25);
						initDocs();
						setProgress(30);

						sheet.circle();
						setProgress(40);

						initQuestions(sheet.getQuestions(),sheet.getoptions(),sheet.getcols(),sheet.getrows(),sheet.avgr());

						setProgress(50);

						String[] results = sheet.getresults();
						setProgress(60);

						sheet.drawgrid();
						setProgress(70);

						outcsv.println(curimgname+","+Arrays.toString(results));
						setProgress(80);

						genrslt(docs,sheet.getstudent(),"OMR", results);
						setProgress(90);

						//docs.push();
						setProgress(100);
						//movefile(file.getPath(),0);
						System.gc();
						System.out.println("Relaseing");
						setProgress(100);
						sheet.release();
						publish(curimgname+"#"+sheet.getQrCode()+"#success");
					} catch (UnableToDetectOptions | WrongFileAttributes | UnableToLoadImage | UnableToDetectMarkers | MappingNotCorrect | CellsWrongDetection | WrongMarkers e1){
						publish(curimgname+"#"+sheet.getQrCode()+"#"+e1.getMessage());
						sheet.release();
						//movefile(file.getPath(),-1);

					}
					/*catch (WrongFileAttributes | UnableToLoadImage | UnableToDetectMarkers  |  WrongMarkers e1){
						publish(curimgname+"#"+sheet.getQrCode()+"#"+e1.getMessage());
						sheet.release();
						//movefile(file.getPath(),-1);

					}
					catch (CouchDbException e2){
					publish(curimgname+"#"+sheet.getQrCode()+"#Error Code 13");
						//movefile(file.getPath(),-1);
					sheet.release();
					} catch (RuntimeException e2){
						e2.printStackTrace();
						publish(curimgname+"#"+sheet.getQrCode()+"#Error Code 7");
						sheet.release();
						//movefile(file.getPath(),-1);
					} catch (CancelException | QrFailedToDetect e1){
						publish(curimgname+"# #Error Code"+e1.getMessage());
						//movefile(file.getPath(),1);
						sheet.release();
					}

					catch(OutOfMemoryError e4)
					{
						e4.printStackTrace();	
						sheet.release();
					}


				}
				return null;
			}
			@Override
			protected void process(List<String> chunks) {
				for(String line: chunks){

					String [] arg = line.split("#");
					display(arg[0],arg[1],arg[2]);
				}
			}
			@Override
			protected void done() {
				view.doCancel();
			}
		};
		worker.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				//System.out.println(evt);
				switch(evt.getPropertyName()){
					case "progress":
						view.SetProgressBarValue((int) evt.getNewValue());
						break;
				}

			}
		});
		worker.execute();
	}*/

	public void display(String ScanSheetName,String DecodedString,String SuccessOrFailure ){
		view.AddRowToTable(ScanSheetName,DecodedString,SuccessOrFailure );
	}
	public String movefile(String fromloc, int stat) throws FileMovedFailed{	
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		Date date = new Date();
		String toloc;
		if(stat == 0){
			toloc = session+"/success";
		}else if (stat == 1){
			toloc = session+"/code";
		}else{
			toloc = session+"/fail";
		}
		try{
			File afile =new File(fromloc);
			if(afile.renameTo(new File(toloc+"/"+dateFormat.format(date)+"_"+afile.getName()))){
			}
		}catch(Exception e){
			throw new FileMovedFailed(fromloc);
		}
		return "successful folder";
	}

	public void closeFiles() throws IOException {
		view.closeFiles();

	}
}
