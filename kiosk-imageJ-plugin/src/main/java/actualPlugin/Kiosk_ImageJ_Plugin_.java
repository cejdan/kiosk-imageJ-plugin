package actualPlugin;

// Imports by category
// Exceptions
import java.io.IOException;

import javax.swing.JFrame;

// Data Structures
import java.io.BufferedReader;
import java.lang.String;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// JSON Specific
import com.google.gson.Gson;

// File Stuff
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.zip.*;

// File Selection
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

// API Request Related
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.HttpURLConnection;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.HttpEntity;

// ImageJ Related
//import ij.*;
//import ij.plugin.PlugIn;
//import ij.process.*;
//import java.awt.*;

import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.text.TextWindow;




/**
 * So far, Kiosk_ImageJ_Plugin lets you select a file type,
 * and it zips directories if selected.
 * // TODO:
 * // Next release
 * // a few jobs at once, async http request, keep sending the request
 * // stretch goal, select multiple file
 * // take at least one file, do multiple jobs
 *
 */
public class Kiosk_ImageJ_Plugin_ implements PlugIn
{
    // Category: Select dialog
    /** 
     * selectFileType helps you can pick a single image or
     * directory. A pop up will guide the user
     * through the steps.
     * @param None
     * @return n, an index of the selected value
     */
    public int selectFileType() {
        Object[] options = {"Not now","Single Image",
                "File of Images"
                };
        int n = JOptionPane.showOptionDialog(null,
                "What type of file do you want to upload? ",
                "File Selection",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        return n;
    }
    
    
    /** 
     * selectJobType helps you can pick from the available job types. 
     * A pop up will guide the user
     * through the steps.
     * @param options, a list of acceptable job types queried
     * @return n, an index of the selected value
     */
    public int selectJobType(Object[] options) {
        int n = JOptionPane.showOptionDialog(null,
                "What type of job would you like? ",
                "Job Selection",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        return n;
    }

    
    /** 
     * minutesForJob() helps you enter how many minutes you are
     * willing to wait for a job.
     * @param None
     * @return selected, a value that has been entered and parsed as
     * an integer.
     */
    public int minutesForJob() {
    	int selected = -1;
    	String defaultSec = "60";
    	while (selected == -1) {
    		JFrame newFrame = new JFrame();
    		try {   			
    		 selected = Integer.parseInt( JOptionPane.showInputDialog(newFrame,
    			        "How long in minutes are you willing to wait for?",
    			        defaultSec));
    		 return selected;
    		}
    		catch (NumberFormatException e) {
    			JOptionPane.showMessageDialog(null, "You did not input a number. Please "
    					+ "input a number.");
    		}
    	}
		return selected;
    }
    
    // Category: File manipulation
    /**
     *  zipFile recursively zips files, through nested directories.
     *  @param file - a file, fileName - a string of the name of the file, an outputstream
     *  to zip
     *  @return None
     */
    private static void zipFile(File file, String fileName, ZipOutputStream zipped) throws 
    																		IOException {
        if (file.isDirectory()) {
            zipped.putNextEntry(new ZipEntry(fileName + (fileName.endsWith("/") ? "" : "/")));
            zipped.closeEntry();
            for (File child : file.listFiles()) {
                zipFile(child, fileName + "/" + child.getName(), zipped);
            }
            return;
        }
        FileInputStream input = new FileInputStream(file);
        zipped.putNextEntry(new ZipEntry(fileName));
        byte[] bytes = new byte[1024];
        int length;
        while ((length = input.read(bytes)) >= 0) {
            zipped.write(bytes, 0, length);
        }
        input.close();
    }

    
    // Category: File selection
    /** 
     * selectDirectory displays a file explorer, which
     * will let you select a specific
     * directory which hopefully contains the batch of
     * images needed to process.
     *  @param None
     *  @return the absolute path of the selected file or null if
     *  a file is not selected
     */
    public String selectDirectory() {
        final List<String> extensions = Arrays.asList("jpg", "png", "gif");
        JFileChooser picker = new JFileChooser();
        picker.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        // Open the file explorer at user.home
        picker.setCurrentDirectory(new File(System.getProperty("user.home")));
        int result = picker.showOpenDialog(null);
        if(result == JFileChooser.APPROVE_OPTION) {
            // If a directory is selected, we want to return its path.
            File selectedFile = picker.getSelectedFile();
            System.out.println("Selected file: " + selectedFile.getAbsolutePath());
            return selectedFile.getAbsolutePath();
        }
        return null;
    }

    
    /** 
     * selectSingleFile displays a file explorer, which
     * will let you select a specific
     * image to process.
     *  @param None
     *  @return the absolute path of the selected file
     *  or null if no file was selected
     */
    public String selectSingleFile() {
    		JFrame jf = new JFrame();
            JFileChooser picker = new JFileChooser();
            jf.add(picker);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            // Ideally, you can only access jpg, gifs, and pngs.
            FileNameExtensionFilter filter =
                    new FileNameExtensionFilter("JPG & GIF Images", "jpg", 
                    		"gif", "png", "tif", "tiff");
            picker.setFileFilter(filter);
            // Open file explorer at User.home
            picker.setCurrentDirectory(new File(System.getProperty("user.home")));
            int result = picker.showOpenDialog(null);
            if(result == JFileChooser.APPROVE_OPTION) {
                // If a file is selected, we want to return its path.
                File selectedFile = picker.getSelectedFile();
                System.out.println("Selected file: " + selectedFile.getAbsolutePath());
                return selectedFile.getAbsolutePath();
            }
            return null;
    }

    
    // Category: API Requests
    /**
     * setupConn sets up basic parameters necessary to establish
     * a connection and send API requests.
     * @param url : The url which we wish to send API requests to
     * @param reqType : POST or GET, usually - type of request
     * @return a valid connection object or null if something goes wrong.
     */
    public HttpURLConnection setupConn(String url, String reqType, boolean setJSON) {
    	try {
	    	URL requestURL = new URL(url);
	        HttpURLConnection conn = (HttpURLConnection) requestURL.openConnection();
	        // Set timeouts for reading and connection
	        conn.setReadTimeout(10000);
	        conn.setConnectTimeout(15000);
	        // We want to make a  request!
	        conn.setRequestMethod(reqType);
	        // We wish to make post and get requests!
	        conn.setDoInput(true);
	        conn.setDoOutput(true);
	        if (setJSON == true) {
	        	conn.setRequestProperty( "Content-Type", "application/json" );
	            conn.setRequestProperty("Accept", "application/json");
	        }
	        return conn;
    	}
    	catch(IOException e) {
    		System.err.print(e);
    		System.out.println("Something went wrong.");
    	}
    	return null;
    }
    
    /**
     * retrieveJSON retrieves the output of the
     * connection, usually a useful JSON object we will use
     * for our purposes
     * @param conn : A connection to the API we are querying
     * @return json_response, a string of the json object output or null
     * if something goes wrong.
     */
    public String retrieveJSON(HttpURLConnection conn) {
    	try {
	    	String json_response = "";
	        // Generate the response, containing the imageURL and new name of the uploaded
	        // file.
	        InputStreamReader response = new InputStreamReader(conn.getInputStream());
	        BufferedReader br = new BufferedReader(response);
	        String text = "";
	        while ((text = br.readLine()) != null) {
	          json_response += text;
	        }
	        System.out.println(json_response);
	    	return json_response;
	    }
    	catch(IOException e) {
    		System.err.print(e);
    		System.out.println("Something went wrong");
    	}
    	return null;
    }
    
    /**
     * uploadFile sends an API request to the server
     * with the selected file.
     * @param file
     * @return JSON response of the upload API request
     */
    public String uploadFile(String file) {
    	try {
    		String url = "http://deepcell.org/api/upload";
    		String reqType = "POST";
        	// Open a connection with the site.
    		HttpURLConnection newConn = setupConn(url, reqType, false);
    		if (newConn != null) {    			
		        // Use the file for selection
		        HttpEntity reqEntity = MultipartEntityBuilder.create()
		                    .addBinaryBody("file", new File(file))
		                    .build();		        		        
		        // Apparently we need this to set the length of content,
		        // and specify type of content.
		        newConn.addRequestProperty("Content-length", 
		        		Objects.toString(reqEntity.getContentLength()));
		        newConn.addRequestProperty(reqEntity.getContentType().getName(), 
		        		reqEntity.getContentType().getValue());
		        // Write in the file to be sent
		        OutputStream os = newConn.getOutputStream();
		        reqEntity.writeTo(newConn.getOutputStream());
		        os.close();
		        newConn.connect();		        
		        if (newConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
		            System.out.println(newConn.getResponseMessage());
		            String json_response = retrieveJSON(newConn);
		            // Generate the response, containing the imageURL and new name of the uploaded
		            // file.
		            newConn.disconnect();
		            return json_response;
		        }
    		}
	    }
    	// Something to do if for some reason this doesn't work.
        catch (IOException ex) {
            System.err.println(ex);
        }
		return null;
    }
    
    /**
     * getJobTypes sends an API request to the server
     * to retrieve all supported job types.
     * @param None
     * @return JSON response of the job types API request, 
     * a string list of all possible job types.
     */
    public String getJobTypes() {
    	try {
        	String url = "http://deepcell.org/api/jobtypes";
        	String reqType = "GET";
        	// Open a connection with the main site.
	        HttpURLConnection conn = setupConn(url, reqType, false);
	        conn.connect();	
	        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
	            System.out.println(conn.getResponseMessage());
	            // Generate the response, containing the list of all job types.
	            String json_response = retrieveJSON(conn);
	            conn.disconnect();
	            return json_response;
	        }
	        else {
	        	System.out.println(conn.getResponseMessage());
	        	System.out.println(conn.getResponseCode());
	            conn.disconnect();
	        }
        }
        catch (IOException ex) {
        	System.out.println("A problem was encountered");
            System.err.println(ex);
            return null;
        }
    	return null;
    }


    
    /**
     * predictFile sends an API request to the server
     * with the selected file.
     * @param file
     * @return JSON response of the upload API request
     */
    public String predictFile(String imageData) {
    	try {
    	String url = "http://deepcell.org/api/predict";
    	String reqType = "POST";
    	// Open a connection with the main site.
	    HttpURLConnection conn = setupConn(url, reqType, true);
	    // Write the message with information about the image
	    // and specified job type.
	    OutputStream os = conn.getOutputStream();
	    OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");   
	    osw.write(imageData);
	    osw.flush();
	    os.close();
	    conn.connect();
	    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
	        System.out.println(conn.getResponseMessage());
	        // Write the JSON response, which is the redisHash.
	        String json_response = retrieveJSON(conn);
	        conn.disconnect();
	        return json_response;
	    }
	    else {
	    	System.out.println(conn.getResponseMessage());
	    	System.out.println(conn.getResponseCode());
	        conn.disconnect();
	    	}
	    }
	    catch (IOException ex) {
	    	System.out.println("A problem was encountered");
	        System.err.println(ex);
	        return null;
	    }
    	return null;
    }
    
    
    /**
     * getStatus queries the status of the current job
     * using its redis hash.
     * @param hash
     * @return json_response, a string with the status or
     * null if something goes wrong. 
     */
    public String getStatus(String hash) {
    	try {
        	String url = "http://deepcell.org/api/status";
        	String reqType = "POST";
        	// Open a connection with the main site.
	        HttpURLConnection conn = setupConn(url, reqType, true);
	        // Write the message we want, namely the redis hash
	        // we want to query the status of.
	        OutputStream os = conn.getOutputStream();
	        byte[] input = hash.getBytes("utf-8");
	        os.write(input, 0, input.length);
	        os.close();
	        conn.connect();	
	        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
	            System.out.println(conn.getResponseMessage());
	            String json_response = retrieveJSON(conn);	            
	            conn.disconnect();
	            return json_response;
	        }
	        else {
	        	System.out.println(conn.getResponseMessage());
	        	System.out.println(conn.getResponseCode());
	            conn.disconnect();
	        	}
        }
        catch (IOException ex) {
        	System.out.println("A problem was encountered");
            System.err.println(ex);
            return null;
        }
    	return null;
    }

    
    /**
     * expireHash queries or sets time limits
     * for expiration.
     * @param expireHash
     * @return json_response, a string, or null
     */
    public String expireHash(String expireHash) {
    	try {
    	String url = "http://deepcell.org/api/redis/expire";
    	String reqType = "POST";
    	// Open a connection with the main site.
	    HttpURLConnection conn = setupConn(url, reqType, true);
	    // Write the message
	    OutputStream os = conn.getOutputStream();
	    OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");   
	    osw.write(expireHash);
	    osw.flush();
	    os.close();
	    conn.connect();	
	    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
	        System.out.println(conn.getResponseMessage());
	        String json_response = retrieveJSON(conn);
	        conn.disconnect();
	        return json_response;	        
	    }
	    else {
	    	System.out.println(conn.getResponseMessage());
	    	System.out.println(conn.getResponseCode());
	        conn.disconnect();
	    	}
    }
    catch (IOException ex) {
    	System.out.println("A problem was encountered");
        System.err.println(ex);
        return null;
    }
	return null;
    }
    
	
    /** 
     * getRedis retrieves the Redis key of the job
     * using a POST request.
     * @param String hash
     * @return String json_response, which should
     *         contain the Redis key.
     */
    public String getRedis(String hash) {
    	try {
    	String url = "http://deepcell.org/api/redis/";
    	String reqType = "POST";
    	// Open a connection with the main site.
	    HttpURLConnection conn = setupConn(url, reqType, true);
	    // Write the message
	    OutputStream os = conn.getOutputStream();
	    OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");   
	    osw.write(hash);
	    osw.flush();
	    os.close();
	    conn.connect();	
	    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
	        System.out.println(conn.getResponseMessage());
	        String json_response = retrieveJSON(conn);
	        conn.disconnect();
	        return json_response;	        
	    }
	    else {
	    	System.out.println(conn.getResponseMessage());
	    	System.out.println(conn.getResponseCode());
	        conn.disconnect();
	    	}
    }
    catch (IOException ex) {
    	System.out.println("A problem was encountered");
        System.err.println(ex);
        return null;
    }
	return null;
    }

 
    
    
    /**
    // 1. Let the person pick if they want images
    // 2. or files only which will be zipped.
    // 3. Upload the image.
     * 4. Queue the job with a hash using Predict.
     * 5. Query job status.
     * 6. Update or retrieve expiration status.
    */
    //public static void main( String[] args )//  throws IOException
    public void run(String arg)//  throws IOException
    {
		//ImagePlus imp = IJ.getImage();
		//IJ.run(imp, "Invert", "");
		//IJ.wait(1000);
		//IJ.run(imp, "Invert", "");
        // If we don't want to exit the program,
        // use this.
        boolean quit = false;
        // Indicator of whether the file type has been picked.
        int fileTypePicked = 0;
        String file = null;
        Kiosk_ImageJ_Plugin_ newApp = new Kiosk_ImageJ_Plugin_();
        while (fileTypePicked == 0) {
            fileTypePicked = newApp.selectFileType();
        }
        // Select image!
        if (fileTypePicked == 1) {
            file = newApp.selectSingleFile();
        }
        // Select a zipped file!
        else {
            // Pick a directory
            file = newApp.selectDirectory();
            if (file != null) {
                // Try to zip the file.
                try {
                    int index = file.lastIndexOf("\\");
                    if (index != -1) {
                        FileOutputStream fos = new FileOutputStream(file + ".zip");
                        ZipOutputStream zipped = new ZipOutputStream(fos);
                        File needsZipping = new File(file);
                        zipFile(needsZipping, needsZipping.getName(), zipped);
                        zipped.close();
                        fos.close();
                        System.out.println(needsZipping.getName());
                        file = file + ".zip";
                    }
                }
                catch(IOException e) {
                    System.out.println("Unable to zip due to " + e + " !");
                }
            }
        }
        if (file == null) {
            System.out.println("Something went wrong");
        }
        else {
            // Start the chain of stuff
            System.out.println("Processing...");

            String charset = "UTF-8";
            // Step 1. Upload the file.
            File uploadFile1 = new File(file);
            String imageData = newApp.uploadFile(file);
            
            // Two issues
            if (imageData != null) {
            	Gson g = new Gson();
            	
            	// Process imageData and set it up for the 
            	// next request, predict.
            	Response response = g.fromJson(imageData, Response.class);
                                
                // Query available job types
            	String getJobTypes = newApp.getJobTypes();
                JobTypes jobTypes = g.fromJson(getJobTypes, JobTypes.class);
                String[] types = jobTypes.getJobTypes();
                int index = newApp.selectJobType(types);
                String jobType = types[index];
                
                // Combine the information we need for predict.
                ImageInfo newImage = new ImageInfo(jobType, response.getuploadedName());
                String theImage = g.toJson(newImage);
                
                // Send next API request, predict, to add image
                // to job queue.
                String newRes = newApp.predictFile(theImage.toString());
            	if (newRes != null) {
            		
            		// Add/query expiration
            		// Add user preference for minutes - done?
            		int minutes = newApp.minutesForJob();
            		int secs = minutes * 60;
            		Hash hashKey = g.fromJson(newRes, Hash.class);
            		ExpireHash newExp = new ExpireHash(hashKey.hash, secs);
            		String expHash = g.toJson(newExp);
            		String setExp = newApp.expireHash(expHash);
            		
            		// Query status: we don't need hash class here, because we're reusing
            		String status = newApp.getStatus(newRes);
            		Status theStatus = g.fromJson(status, Status.class);           		
            		String prevStatus = theStatus.status;
            		String updatedStatus = prevStatus;
            		while ((updatedStatus.compareTo("null") != 0) && (updatedStatus.compareTo("failed") != 0) && (updatedStatus.compareTo("done") != 0)) {
            			System.out.println(updatedStatus);
            			while (updatedStatus.compareTo(prevStatus) == 0) {
            				try {
								Thread.sleep(60000);
								// Query status: we don't need hash class here, because we're reusing
			            		status = newApp.getStatus(newRes);
			            		theStatus = g.fromJson(status, Status.class);
			            		updatedStatus = theStatus.status;
			            		if (updatedStatus != prevStatus) {
			            			System.out.println("Status changed!");
			            			System.out.println(updatedStatus);
			            		}								
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								 e.printStackTrace();
							}
            			}
            			prevStatus = updatedStatus;
            		}
            		if (updatedStatus.compareTo("failed") == 0) {
            			System.out.println("FAILURE!!!");
            			newRes = g.toJson(new GetError(hashKey.hash, "reason"));
            			System.out.println(newApp.getRedis(newRes));
            			new TextWindow("FAILURE!!!", newRes,  450, 450);
            		}
            		if (updatedStatus.compareTo("done") == 0) {
            			System.out.println("DONE!!!");
            			newRes = g.toJson(new GetError(hashKey.hash, "output_url"));
            			System.out.println(newApp.getRedis(newRes));
            			new TextWindow("DONE!!!", newRes,  450, 450);
            		}
            	}
            	else {
            		System.out.println("No!");
            	}
            }
        }        
        System.out.println("End of program");
    }
}