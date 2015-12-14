package org.camunda.bpm.cycle.connector.trisotech;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;






import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.camunda.bpm.cycle.connector.ConnectorNode;
import org.camunda.bpm.cycle.connector.ConnectorNodeType;
import org.camunda.bpm.cycle.exception.CycleException;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;


public class TrisotechClient {
	
	private static final Logger logger = Logger.getLogger(TrisotechClient.class.getName());

	  private static final String UTF_8 = "UTF-8";
	  static final String SLASH_CHAR = "/"; //NOTE!: this is also defined in TrisotechJson.java so if you change it here, change it there as well.

	  private static final int MAX_OPEN_CONNECTIONS_TOTAL = 20;
	  private static final int MAX_OPEN_CONNECTIONS_PER_ROUTE = 5;
	  private static final int CONNECTION_IDLE_CLOSE = 2000;
	  private static final int CONNECTION_TIMEOUT = 3000;
	  private static final int CONNECTION_TTL = 5000;
	  private static final int RETRIES_CONNECTION_EXCEPTION = 1;
	  
	  private static final String LOGIN_URL_SUFFIX = "login";
	  private static final String REPOSITORY_URL_SUFFIX = "repository"; // |personal|
	  private static final String REPOSITORY_CONTENT_URL_SUFFIX = "repositorycontent";
	  private static final String MIMETYPE_URL_SUFFIX = "application/bpmn-2-0+xml";
	  
	  private static final String DEFAULT_BPMN_FILE_LOCATION = "EmptyTrisotechFile.bpmn";
	  private static final String CYCLE_TEMP_DIR =	"camunda_cycle";
	  
	  private DefaultHttpClient apacheHttpClient;
	  private Executor requestExecutor;

	  private String configurationName;
	  private String trisotechBaseUrl; //The default should be - https://cloud.trisotech.com/publicapi/
	  private String proxyUrl;
	  private String proxyUsername;
	  private String proxyPassword;

	  private String username;
	  private String password;

	  private String authToken;
	  

	public TrisotechClient(String configurationName, String trisotechBaseUrl, String proxyUrl, String proxyUsername,
		      String proxyPassword) throws URISyntaxException {
		  
		    this.configurationName = configurationName;
		    this.trisotechBaseUrl = trisotechBaseUrl;
		    this.proxyUrl = proxyUrl;
		    this.proxyUsername = proxyUsername;
		    this.proxyPassword = proxyPassword;

		    initHttpClient();
	 }
	  
	  
	  private void initHttpClient() throws URISyntaxException {
		    // trust all certificates
		    SchemeRegistry schemeRegistry = SchemeRegistryFactory.createDefault();
		    try {
		      SSLContext sslContext = SSLContext.getInstance("SSL");

		      X509TrustManager trustAllManager = new X509TrustManager() {
		        
		        public X509Certificate[] getAcceptedIssuers() {
		          return null;
		        }

		        public void checkClientTrusted(X509Certificate[] certs, String authType) {
		        }

		        public void checkServerTrusted(X509Certificate[] certs, String authType) {
		        }
		      };

		      // set up a TrustManager that trusts everything
		      sslContext.init(new KeyManager[0], new TrustManager[] { trustAllManager }, new SecureRandom());
		      SSLContext.setDefault(sslContext);

		      SSLSocketFactory sslSF = new SSLSocketFactory(sslContext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

		      // set up an own X509HostnameVerifier because default one is still
		      // too strict.
		      sslSF.setHostnameVerifier(new X509HostnameVerifier() {
		        public void verify(String s, SSLSocket sslSocket) throws IOException {
		        }

		        public void verify(String s, X509Certificate x509Certificate) throws SSLException {
		        }

		        public void verify(String s, String[] strings, String[] strings2) throws SSLException {
		        }

		        public boolean verify(String s, SSLSession sslSession) {
		          return true;
		        }
		      });

		      schemeRegistry.register(new Scheme("https", 443, sslSF));
		    } catch (Exception e) {
		      logger.log(Level.SEVERE, "Unable to modify SSLSocketFactory to allow self-signed certificates.", e);
		    }

		    // configure connection params
		    SyncBasicHttpParams params = new SyncBasicHttpParams();
		    DefaultHttpClient.setDefaultHttpParams(params);
		    HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
		    HttpConnectionParams.setStaleCheckingEnabled(params, true);
		    HttpConnectionParams.setLinger(params, 5000);

		    // configure thread-safe client connection management
		    final PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager(schemeRegistry,
		        CONNECTION_TTL, TimeUnit.MILLISECONDS);
		    connectionManager.setDefaultMaxPerRoute(MAX_OPEN_CONNECTIONS_PER_ROUTE);
		    connectionManager.setMaxTotal(MAX_OPEN_CONNECTIONS_TOTAL);

		    // configure and initialize apache httpclient
		    apacheHttpClient = new DefaultHttpClient(connectionManager, params);

		    configureProxy();

		    // configure proxy stuff
		    apacheHttpClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {
		      private int retries = RETRIES_CONNECTION_EXCEPTION;

		      public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
		        if (exception == null) {
		          throw new IllegalArgumentException("Exception parameter may not be null");
		        }
		        if (context == null) {
		          throw new IllegalArgumentException("HTTP context may not be null");
		        }
		        if (exception instanceof ConnectTimeoutException && retries > 0) {
		          // Timeout
		          retries--;
		          return true;
		        }

		        return false;
		      }
		    });

		    // close expired / idle connections and add securityToken header for
		    // each request
		    apacheHttpClient.addRequestInterceptor(new HttpRequestInterceptor() {
		      public void process(HttpRequest request, HttpContext ctx) throws HttpException, IOException {
		        connectionManager.closeExpiredConnections();
		        connectionManager.closeIdleConnections(CONNECTION_IDLE_CLOSE, TimeUnit.MILLISECONDS);

		        String uri = request.getRequestLine().getUri().toString();
		        logger.fine("Sending request to " + uri);
		        logger.fine("RequestHeaders: " + request.getAllHeaders());
		      }
		    });

		    apacheHttpClient.addResponseInterceptor(new HttpResponseInterceptor() {
		      public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
		        logger.fine("Received response with status " + response.getStatusLine().getStatusCode());
		      }
		    });

		    apacheHttpClient.setReuseStrategy(new NoConnectionReuseStrategy());

		    requestExecutor = Executor.newInstance(apacheHttpClient);
		  }
	  
	  private void configureProxy() throws URISyntaxException {
		 if (proxyUrl != null && !proxyUrl.isEmpty()) {
		      URI proxyURI = new URI(proxyUrl);
		      String proxyHost = proxyURI.getHost();
		      int proxyPort = proxyURI.getPort();
		      apacheHttpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(proxyHost, proxyPort));

		      if (proxyUsername != null && !proxyUsername.isEmpty() && proxyPassword != null && !proxyPassword.isEmpty()) {
		    	  	apacheHttpClient.getCredentialsProvider().setCredentials(new AuthScope(proxyHost, proxyPort),
		            new UsernamePasswordCredentials(proxyUsername, proxyPassword));
		      }
		      logger.fine("Configured tristech client with proxy settings: url: " + proxyUrl + ", proxyUsername: " + proxyUsername);
		 }
	}
	 
	  public boolean login(String username, String password) {   
		    
//	This is being commented out until trisotech sort out their login REST call
//		  
//		  URIBuilder builder = new URIBuilder()
//		    	.setScheme("https")
//	            .setHost(requestUrl(LOGIN_URL_SUFFIX).toString())
//	            .setParameter("username", username)
//	            .setParameter("password", password)
//	            .setParameter("mode", "json");
//		    	

		// Until the login works - as a temporary measure, we're going to use a test token. 
		  String testToken = "a16e1c9b-e0c4-4f17-9c03-359faf24429a";
		  
		  // https://cloud.trisotech.com/publicapi/login?mode=xml&authtoken=
		  URIBuilder builder = new URIBuilder()
	    	.setScheme("https")
	    	.setHost(requestUrl(LOGIN_URL_SUFFIX).toString())
	    	.setParameter("mode", "json")
		  	.setParameter("authtoken", testToken);
		    
		    URI uri = null;
		    try {
				uri = builder.build();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}

		    		    
		    //System.out.println(uri.toString());
		    Request request = Request.Post(uri);
		    			request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
		    HttpResponse response = executeAndGetResponse(request);
		    
		    String responseResult = extractResponseResult(response);
		   // System.out.println(responseResult);
		    if (responseResult == null || responseResult.equals("")) {
		      throw new CycleException("Could not login into connector '" + configurationName + "'. The user name and/or password might be incorrect.");
		    }
		    
		    String authTokenTemp = "";
		    
		    try {
		    	authTokenTemp = TrisotechJson.extractNodeToken(new JSONObject(responseResult));
				
			} catch (JSONException e) {
				
				e.printStackTrace();
			}
		    
		     setAuthToken(authTokenTemp);
		     logger.fine("AuthToken: " + authToken);
	    
		    return true;
		  }
	  
	  public String getRepositories()
	  {
		  //https://cloud.trisotech.com/publicapi/repository?authtoken=a16e1c9b-e0c4-4f17-9c03-&mode=json
		  URIBuilder builder = new URIBuilder()
	    	.setScheme("https")
	    	.setHost(requestUrl(REPOSITORY_URL_SUFFIX).toString())
	    	.setParameter("mimetype", MIMETYPE_URL_SUFFIX)
	    	.setParameter("mode", "json")
		  	.setParameter("authtoken", getAuthToken());
		    
		    URI uri = null;
		    try {
				uri = builder.build();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}

		    		    
		    //System.out.println(uri.toString());
		    Request request = Request.Get(uri);
		    			request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
		    HttpResponse response = executeAndGetResponse(request);
		    
		    String responseResult = extractResponseResult(response);
		   // System.out.println(responseResult);
		    
		    return responseResult;
		  
	  }
	  
	  
	  private URI requestUrl(String... pathArgs) {
		    try {
		      URIBuilder builder = new URIBuilder(trisotechBaseUrl);
		      StringBuffer sb = new StringBuffer();
		      for (String pathArg : pathArgs) {
		        if (!pathArg.startsWith(SLASH_CHAR)) {
		          sb.append(SLASH_CHAR);
		        }
		        sb.append(pathArg);
		      }
		      builder.setPath(builder.getPath() + sb.toString());
		      
		      URI requestURI = builder.build();
		      logger.fine(requestURI.toString());
		     // System.out.println(requestURI.toString());
		      return requestURI;
		    } catch (URISyntaxException e) {
		      throw new CycleException("Failed to construct url for signavio request.", e);
		    }
		  }
	  

	private String ExecuteCommand(Form form, String programmCode, String command) {
	    return ExecuteCommand(form, programmCode, command, true);
	  }

	  private String ExecuteCommand(Form form, String programmCode, String command, boolean doRelogin) {
	    Request request = Request.Post(getRequestUrl(programmCode, command)).bodyForm(form.build(), Charset.forName(UTF_8));

	    HttpResponse response = executeAndGetResponse(request);
	    String responseText = extractResponseResult(response);
	    if (response.getStatusLine().getStatusCode() == 500) {
	      try {
	        JSONObject obj = new JSONObject(responseText);
	     // TODO Once we have a perminant login sorted - work out what to do if at this point log in isn't working
//	        if (obj.has("errorType")) {
//	          if (obj.get("errorType").equals("SessionNotFound")) {
//	            if (doRelogin && relogin()) {
//	              return ExecuteCommand(form, programmCode, command, false);
//	            }
//	          }
//	        }
	      } catch (JSONException e) {
	        e.printStackTrace();
	      }
	      return null;
	    }
	    return responseText;
	  }
	  
	  private String extractResponseResult(HttpResponse response) {
		    try {
		      String payload = EntityUtils.toString(response.getEntity(), Charset.forName(UTF_8));
		      if (payload.contains("An error occurred (unauthorized)")) {
		        throw new CycleException("Could not login into connector '" + configurationName
		            + "'. The user name and/or password might be incorrect.");
		      }
		      return payload;
		    } catch (IOException e) {
		      throw new CycleException(e.getMessage(), e);
		    } finally {
		      HttpClientUtils.closeQuietly(response);
		    }
		  }
	  
	  private String getRequestUrl(String programmCode, String cmd) {
		    StringBuilder sb = new StringBuilder();

		    sb.append(trisotechBaseUrl);

		    if (!trisotechBaseUrl.endsWith("/")) {
		      sb.append("/");
		    }
		    //sb.append("ExecuteCommand");
		    sb.append("?");
		    sb.append("programmCode=").append(programmCode);
		    sb.append("&");
		    sb.append("command=").append(cmd);

		    return sb.toString();
		  }
	  
	  protected HttpResponse executeAndGetResponse(Request request) {
		    try {
		    	return requestExecutor.execute(request).returnResponse();
		    } catch (Exception e) {
		      throw new CycleException("Connector '" + configurationName + "'", e);
		    }
		  }
	  
//	  public boolean relogin() {
//		    if (username != null && password != null) {
//		      return loginWithHashPassword(username, password);
//		    } else {
//		      return false;
//		    }
//		  }

	
	public String getAuthToken(){
		return this.authToken;
	}
	private void setAuthToken(String newAuthToken){
		this.authToken = newAuthToken;
	}
	
//	public String getFileNodeJson(ConnectorNode filenode)
//	{
//		URIBuilder builder = new URIBuilder()
//    	.setScheme("https")
//    	.setHost(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString())
//    	.setParameter("repository", getRespositoryName(filenode))
//    	.setParameter("mode", "json")
//    	.setParameter("mimetype", MIMETYPE_URL_SUFFIX)
//	  	.setParameter("authtoken", getAuthToken())
//		.setParameter("path", getPathFromNode(filenode)); // This is the path to the file
//	    
//	    URI uri = null;
//	    try {
//			uri = builder.build();
//		} catch (URISyntaxException e) {
//			e.printStackTrace();
//		}
//
//	    		    
//	    //System.out.println(uri.toString());
//	    Request request = Request.Get(uri);
//	    			request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
//	    HttpResponse response = executeAndGetResponse(request);
//	    
//	    String responseResult = extractResponseResult(response);
//
//	
//	return responseResult;
//
//	}


	public String getChildren(TrisotechConnectorNode parent) {
	
		URIBuilder builder = new URIBuilder()
	    	.setScheme("https")
	    	.setHost(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString())
	    	.setParameter("repository", getRespositoryName(parent))
	    	.setParameter("mode", "json")
	    	.setParameter("mimetype", MIMETYPE_URL_SUFFIX)
		  	.setParameter("authtoken", getAuthToken())
		  	.setParameter("path", parent.getTrisotechPath()); 
		    
		    URI uri = null;
		    try {
				uri = builder.build();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}

		    		    
		   // System.out.println(uri.toString());
		    Request request = Request.Get(uri);
		    			request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
		    HttpResponse response = executeAndGetResponse(request);
		    
		    String responseResult = extractResponseResult(response);

		
		return responseResult;
	}
	
	  private String getPathFromNode(ConnectorNode parent) 
	  {
		String parentID = parent.getId();
		if(!parentID.contains(SLASH_CHAR)){
			return SLASH_CHAR;
		}
		else if(parentID.indexOf(SLASH_CHAR) == parentID.length()-1) // So this tells us the first occurrence of a slash Character is also the last character - so this is a repository so we have to remove the name
		{
			return SLASH_CHAR;
		}else{
			String pathWithOutRepository = parentID.substring(parentID.indexOf(SLASH_CHAR), parentID.length());
			return pathWithOutRepository;
		}
		
	}


	public String getRespositoryName(TrisotechConnectorNode parent) 
	  {
		  String id = parent.getId();
		  if(id.indexOf(SLASH_CHAR) == -1)
		  {
			  return parent.getId(); // Turns out this is a repository and has no "path" 
			  
		  }else{
			  String rep = id.substring(0, id.indexOf(TrisotechClient.SLASH_CHAR));
			  return rep;
		  }
		  
		  
	}
	  
	  
	public String createNewEmptyFile(TrisotechConnectorNode parent, String label) {
		
		TrisotechConnectorNode tempNode = new TrisotechConnectorNode();
		tempNode.setType(ConnectorNodeType.BPMN_FILE);
		tempNode.setLabel(label);
		tempNode.setId(parent.getId() + SLASH_CHAR + label);
		tempNode.setTrisotechPath(getPathFromNode(parent)); // this is being set so when the file is saved the rest call can be generated easily
				
		String returnString = "";

		
		try {
			
			//For testing this gets the file - in productions it's likely to return null
			InputStream input = getClass().getClassLoader().getResourceAsStream(DEFAULT_BPMN_FILE_LOCATION);
			
			if(input == null)
			{
				input = getClass( ).getResourceAsStream("resources/" + DEFAULT_BPMN_FILE_LOCATION);
			}
			
			returnString = createFileInRepository(tempNode, input, "");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		return returnString;
	}


	public String createFolder(TrisotechConnectorNode parent, String label) {

		URIBuilder builder = new URIBuilder()
    	.setScheme("https")
    	.setHost(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString())
    	.setParameter("repository", getRespositoryName(parent))
    	.setParameter("mode", "json")
    	.setParameter("authtoken", getAuthToken())
    	.setParameter("path", parent.getTrisotechPath()+label); // we're adding the parent directory and the name of the file together. 
		//.setParameter("path", getPathFromNode(parent)+label); 
	    
	    URI uri = null;
	    try {
			uri = builder.build();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
 		
	    
	    
	    
	   // System.out.println(uri.toString());
	    Request request = Request.Post(uri);
	    			request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
	    HttpResponse response = executeAndGetResponse(request);
	    
	    String responseResult = extractResponseResult(response);

		
		return responseResult;
	}


	public InputStream getXmlFile(TrisotechConnectorNode fileNode) 
	{
		// In order to the URL of the file i need to first the parent directory. Currently there is no way directly get file contents with the file URL
		String fileURL = fileNode.getFileLocation();
		try {
			InputStream input = new URL(fileURL).openStream();
			return input;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public String createFileInRepository(TrisotechConnectorNode node, InputStream newContent, String message) {
		
		URIBuilder builder = new URIBuilder()
    	.setScheme("https")
    	.setHost(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString())
    	.setParameter("repository", getRespositoryName(node))
    	.setParameter("mimetype", MIMETYPE_URL_SUFFIX)
    	.setParameter("mode", "json")
    	.setParameter("authtoken", getAuthToken())
    	.setParameter("path", node.getTrisotechPath()); // this should be the name of the path without the filename attached
		
	    URI uri = null;
	    try {
			uri = builder.build();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	    
	    logger.log(Level.INFO, builder.toString());
	    System.out.println(builder.toString());
	    
	    HttpClient httpclient = new DefaultHttpClient();
	    HttpPost httpPost = new HttpPost(uri);

	    OutputStream outputStream;
	    
		try {

			
			File newContentfile = createFile(getFileName(node, true));
			outputStream = new FileOutputStream(newContentfile);
			IOUtils.copy(newContent, outputStream);
		    outputStream.close();

		    FileBody uploadFilePart = new FileBody(newContentfile);
		    MultipartEntity reqEntity = new MultipartEntity();
		    reqEntity.addPart("file", uploadFilePart);
		    httpPost.setEntity(reqEntity);

		    HttpResponse response;
		    
		    response = httpclient.execute(httpPost);
			String responseResult = extractResponseResult(response);
			
			//now to clean up the temp file. 
			newContentfile.delete();
			
			return responseResult;
		    
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return "";

	}
	
	private File createFile(String fileName) 
	{
		String tempDirProperty = "java.io.tmpdir";
		String fileSparator = System.getProperty("file.separator");

	    String tempDir = System.getProperty(tempDirProperty); //<HOME>\AppData\Local\Temp\
  
	    File newFile = new File(tempDir+CYCLE_TEMP_DIR+fileSparator+fileName);
	    File parent = newFile.getParentFile();
	    if(!parent.exists())
	    	parent.mkdirs();
	    
		return newFile;
	}


	public String updateContent(TrisotechConnectorNode node, InputStream newContent, String message) {
		
		//Need to get rid of the file from the trisotech repository before the new one goes up.
		//deleteFile(node);
		
		//Now i can try to upload the new file.
		return createFileInRepository(node, newContent, message);
		
	}
		
		
	private String getFileName(TrisotechConnectorNode node, boolean includeExtention)
	{
		  String id = node.getId();
		  String fileName = "";
		  if(id.indexOf(SLASH_CHAR) == -1)
		  {
			  return node.getId(); // Turns out this is a repository and has no "path" 
			  
		  }else{
			  if(includeExtention){
				  fileName  = id.substring(id.lastIndexOf(TrisotechClient.SLASH_CHAR)+1, id.length());
			  }else{
				  fileName  = id.substring(id.lastIndexOf(TrisotechClient.SLASH_CHAR)+1, id.lastIndexOf('.'));
			  }
				 
			  return fileName;
		  }
		
	}


	public String deleteFolder(TrisotechConnectorNode node) {
		
		URIBuilder builder = new URIBuilder()
    	.setScheme("https")
    	.setHost(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString())
    	.setParameter("repository", getRespositoryName(node))
    	.setParameter("mode", "json")
    	.setParameter("authtoken", getAuthToken())
		.setParameter("id", node.getTrisotechPath()); // The trisotech path of a folder node contains it's name e.g. /test1/thisfolder - so no need to add the "lable"
	    
	    URI uri = null;
	    try {
			uri = builder.build();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

	    		    
	   // System.out.println(uri.toString());
	    Request request = Request.Delete(uri);
	    			request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
	    HttpResponse response = executeAndGetResponse(request);
	    
	    String responseResult = extractResponseResult(response);

		
		return responseResult;

		
	}


	public String deleteFile(TrisotechConnectorNode node) {
		
		URIBuilder builder = new URIBuilder()
    	.setScheme("https")
    	.setHost(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString())
    	.setParameter("repository", getRespositoryName(node))
    	.setParameter("mode", "json")
    	.setParameter("authtoken", getAuthToken())
    	.setParameter("mimetype", MIMETYPE_URL_SUFFIX)
		.setParameter("sku", node.getTrisotechPath()+node.getLabel()); // The "label" in this case is in fact the ID on there side.
	    
	    URI uri = null;
	    try {
			uri = builder.build();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		
	    
	    Request request = Request.Delete(uri);
		request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
		HttpResponse response = executeAndGetResponse(request);
		
		String responseResult = extractResponseResult(response);
		
		return responseResult;

	}


}
