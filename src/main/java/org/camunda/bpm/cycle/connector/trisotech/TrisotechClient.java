package org.camunda.bpm.cycle.connector.trisotech;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.camunda.bpm.cycle.connector.ConnectorNodeType;
import org.camunda.bpm.cycle.exception.CycleException;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class TrisotechClient {

    private static final Logger LOGGER = Logger.getLogger(TrisotechClient.class.getName());

    private static final String UTF_8 = "UTF-8";

    static final String SLASH_CHAR = "/";

    private static final int MAX_OPEN_CONNECTIONS_TOTAL = 20;

    private static final int MAX_OPEN_CONNECTIONS_PER_ROUTE = 5;

    private static final int CONNECTION_IDLE_CLOSE = 2000;

    private static final int CONNECTION_TIMEOUT = 3000;

    private static final int CONNECTION_TTL = 5000;

    private static final int RETRIES_CONNECTION_EXCEPTION = 1;

    private static final String LOGIN_URL_SUFFIX = "login";

    private static final String REPOSITORY_URL_SUFFIX = "repository";

    private static final String REPOSITORY_CONTENT_URL_SUFFIX = "repositorycontent";

    private static final String MIMETYPE_URL_SUFFIX = "application/bpmn-2-0+xml";

    private static final String DEFAULT_BPMN_FILE_LOCATION = "EmptyTrisotechFile.bpmn";

    private DefaultHttpClient apacheHttpClient;

    private Executor requestExecutor;

    private String configurationName;

    private String trisotechBaseUrl; // The default should be - https://cloud.trisotech.com

    private String proxyUrl;

    private String proxyUsername;

    private String proxyPassword;

    private String password;

    public TrisotechClient(String configurationName, String trisotechBaseUrl, String proxyUrl, String proxyUsername, String proxyPassword)
            throws URISyntaxException {

        this.configurationName = configurationName;
        this.trisotechBaseUrl = trisotechBaseUrl;
        // Correct user inputted url
        if (this.trisotechBaseUrl.endsWith("/")) {
            this.trisotechBaseUrl = this.trisotechBaseUrl.substring(0, this.trisotechBaseUrl.length() - 1);
        }
        if (!this.trisotechBaseUrl.endsWith("/publicapi")) {
            this.trisotechBaseUrl = this.trisotechBaseUrl + "/publicapi";
        }
        if (!this.trisotechBaseUrl.startsWith("http")) {
            this.trisotechBaseUrl = "https://" + this.trisotechBaseUrl;
        }
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
            LOGGER.log(Level.SEVERE, "Unable to modify SSLSocketFactory to allow self-signed certificates.", e);
        }

        // configure connection params
        SyncBasicHttpParams params = new SyncBasicHttpParams();
        DefaultHttpClient.setDefaultHttpParams(params);
        HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
        HttpConnectionParams.setStaleCheckingEnabled(params, true);
        HttpConnectionParams.setLinger(params, 5000);

        // configure thread-safe client connection management
        final PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager(schemeRegistry, CONNECTION_TTL, TimeUnit.MILLISECONDS);
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
                LOGGER.fine("Sending request to " + uri);
            }
        });

        apacheHttpClient.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
                LOGGER.fine("Received response with status " + response.getStatusLine().getStatusCode());
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
            LOGGER.fine("Configured tristech client with proxy settings: url: " + proxyUrl + ", proxyUsername: " + proxyUsername);
        }
    }

    public boolean login(String username, String password) {
        // Username is ignored
        this.password = password;

        LOGGER.fine("Login to Trisotech connector with token (password): " + password.substring(0, 4) + "...");

        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(requestUrl(LOGIN_URL_SUFFIX).toString());
            uri = builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Request request = Request.Get(uri);
        request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        request.addHeader("authtoken", password);

        HttpResponse response = executeAndGetResponse(request);

        String responseResult = extractResponseResult(response);
        String email = null;
        try {
            email = TrisotechJson.extractEmail(new JSONObject(responseResult));
            if (responseResult == null || responseResult.equals("") || responseResult.contains("InvalidMemberCredentials") || email == null) {
                throw new CycleException("Could not login into connector '" + configurationName
                        + "'. The user name and/or password might be incorrect. Make sure that you use the AuthToken obtained from " + trisotechBaseUrl
                        + "/login as your password.");
            }

            LOGGER.fine("Logged is as: " + email);

        } catch (JSONException e) {
            LOGGER.log(Level.WARNING, "Error logging in to Trisotech Connector", e);
        }
        if (email == null) {
            throw new CycleException("Could not login to the Trisotech connector");
        }

        return email != null;
    }

    public String getRepositories() {

        LOGGER.fine("Retrieving repositories");

        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(requestUrl(REPOSITORY_URL_SUFFIX).toString());
            uri = builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Request request = Request.Get(uri);
        request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        request.addHeader("authtoken", password);
        HttpResponse response = executeAndGetResponse(request);

        String responseResult = extractResponseResult(response);

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
            LOGGER.fine(requestURI.toString());
            return requestURI;
        } catch (URISyntaxException e) {
            throw new CycleException("Failed to construct url for trisotech request.", e);
        }
    }

    private String extractResponseResult(HttpResponse response) {
        try {
            String payload = EntityUtils.toString(response.getEntity(), Charset.forName(UTF_8));
            if (payload.contains("An error occurred (unauthorized)")) {
                throw new CycleException("Could not login into connector '" + configurationName + "'. The user name and/or password might be incorrect.");
            }
            return payload;
        } catch (IOException e) {
            throw new CycleException(e.getMessage(), e);
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    protected HttpResponse executeAndGetResponse(Request request) {
        try {
            return requestExecutor.execute(request).returnResponse();
        } catch (Exception e) {
            throw new CycleException("Connector '" + configurationName + "'", e);
        }
    }

    public String getChildren(TrisotechConnectorNode parent) {
        LOGGER.fine("Retrieving BPMN repository content of '" + parent.getRepositoryId() + "' at path '" + parent.getPath() + "'");

        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString()).setParameter("repository", parent.getRepositoryId())
                    .setParameter("mimetype", MIMETYPE_URL_SUFFIX).setParameter("path", parent.getPath());
            uri = builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Request request = Request.Get(uri);
        request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        request.addHeader("authtoken", password);

        HttpResponse response = executeAndGetResponse(request);

        String responseResult = extractResponseResult(response);

        return responseResult;
    }

    public String createNewEmptyFile(TrisotechConnectorNode parent, String label) {
        LOGGER.fine("Creating new BPMN file in repository  '" + parent.getRepositoryId() + "' at path '" + parent.getPath() + "' with name " + label);

        if (label.endsWith(".bpmn")) {
            label = label.substring(0, label.length() - ".bpmn".length());
        }
        TrisotechConnectorNode tempNode = new TrisotechConnectorNode();
        tempNode.setType(ConnectorNodeType.BPMN_FILE);

        try {

            // For testing this gets the file - in productions it's likely to return null
            InputStream input = getClass().getClassLoader().getResourceAsStream(DEFAULT_BPMN_FILE_LOCATION);

            if (input == null) {
                input = getClass().getResourceAsStream("resources/" + DEFAULT_BPMN_FILE_LOCATION);
            }

            URI uri = null;
            try {
                URIBuilder builder = new URIBuilder(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString()).setParameter("repository", parent.getRepositoryId())
                        .setParameter("mimetype", MIMETYPE_URL_SUFFIX).setParameter("name", label).setParameter("path", parent.getPath());
                uri = builder.build();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(uri);
            httpPost.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
            httpPost.addHeader("authtoken", password);

            InputStreamBody uploadFilePart = new InputStreamBody(input, label);
            MultipartEntity reqEntity = new MultipartEntity();
            reqEntity.addPart("file", uploadFilePart);
            httpPost.setEntity(reqEntity);

            HttpResponse response = httpclient.execute(httpPost);
            String responseResult = extractResponseResult(response);

            return responseResult;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public String createFolder(TrisotechConnectorNode parent, String label) {
        LOGGER.fine("Creating folder in repository '" + parent.getRepositoryId() + "' at path '" + parent.getPath() + "' with name '" + label + "'");

        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString()).setParameter("repository", parent.getRepositoryId())
                    .setParameter("path", (parent.getPath().length() > 0 ? parent.getPath() : "") + SLASH_CHAR + label);
            uri = builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Request request = Request.Post(uri);
        request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        request.addHeader("authtoken", password);

        HttpResponse response = executeAndGetResponse(request);

        String responseResult = extractResponseResult(response);

        return responseResult;
    }

    public InputStream getXmlFile(TrisotechConnectorNode fileNode) {

        LOGGER.fine("Downloading BPMN file in repository '" + fileNode.getRepositoryId() + "' at path '" + fileNode.getPath() + "' with name '"
                + fileNode.getLabel() + "' at URL '" + fileNode.getURL() + "'");

        // In order to the URL of the file i need to first the parent directory. Currently there is no way directly get file contents with the file URL
        String fileURL = fileNode.getURL();

        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(fileURL);

            uri = builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Request request = Request.Get(uri);
        request.addHeader("authtoken", password);

        HttpResponse response = executeAndGetResponse(request);

        try {
            return response.getEntity().getContent();
        } catch (IOException e) {
            throw new CycleException(e.getMessage(), e);
        }
    }

    public String updateFileInRepository(TrisotechConnectorNode node, InputStream newContent, String message) {

        LOGGER.fine("Updating BPMN file in repository '" + node.getRepositoryId() + "' at path '" + node.getPath() + "' with name '" + node.getLabel()
                + "' at URL '" + node.getURL() + "'");

        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(node.getURL());
            uri = builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(uri);
        httpPost.addHeader("authtoken", password);
        httpPost.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());

        InputStreamBody uploadFilePart = new InputStreamBody(newContent, node.getLabel());
        MultipartEntity reqEntity = new MultipartEntity();
        reqEntity.addPart("file", uploadFilePart);
        httpPost.setEntity(reqEntity);

        HttpResponse response;

        try {
            response = httpclient.execute(httpPost);
            String responseResult = extractResponseResult(response);
            return responseResult;
        } catch (IOException e) {
            throw new CycleException(e);
        }

    }

    public String deleteFolder(TrisotechConnectorNode node) {
        LOGGER.fine("Delete folder in repository '" + node.getRepositoryId() + "' at path '" + node.getPath() + "' with name '" + node.getLabel() + " with id '"
                + node.getTrisotechId() + "'");

        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString()).setParameter("repository", node.getRepositoryId())
                    .setParameter("id", node.getTrisotechId());

            uri = builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Request request = Request.Delete(uri);
        request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        request.addHeader("authtoken", password);
        HttpResponse response = executeAndGetResponse(request);

        String responseResult = extractResponseResult(response);

        return responseResult;

    }

    public String deleteFile(TrisotechConnectorNode node) {
        LOGGER.fine("Delete file in repository '" + node.getRepositoryId() + "' at path '" + node.getPath() + "' with name '" + node.getLabel() + " with id '"
                + node.getTrisotechId() + "'");

        URI uri = null;
        try {
            URIBuilder builder = new URIBuilder(requestUrl(REPOSITORY_CONTENT_URL_SUFFIX).toString()).setParameter("repository", node.getRepositoryId())
                    .setParameter("id", node.getTrisotechId());
            uri = builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Request request = Request.Delete(uri);
        request.addHeader("accept", ContentType.APPLICATION_JSON.getMimeType());
        request.addHeader("authtoken", password);
        HttpResponse response = executeAndGetResponse(request);

        String responseResult = extractResponseResult(response);

        return responseResult;

    }

}
