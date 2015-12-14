/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.cycle.connector.trisotech;


import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


import javax.inject.Inject;

import org.camunda.bpm.cycle.configuration.CycleConfiguration;
import org.camunda.bpm.cycle.connector.Connector;
import org.camunda.bpm.cycle.connector.ConnectorNode;
import org.camunda.bpm.cycle.connector.ConnectorNodeType;
import org.camunda.bpm.cycle.connector.ContentInformation;
import org.camunda.bpm.cycle.connector.Secured;
import org.camunda.bpm.cycle.connector.signavio.SignavioClient;
import org.camunda.bpm.cycle.entity.ConnectorConfiguration;
import org.camunda.bpm.cycle.exception.CycleException;
import org.camunda.bpm.cycle.util.IoUtil;
import org.springframework.stereotype.Component;

/**
 * An example connector implementation which persists BPMN files to a
 * simple memory based map.
 *
 */
@Component
public class TrisotechConnector extends Connector {

  @Inject
  private CycleConfiguration cycleConfiguration;
  
  public final static String CONFIG_KEY_TRISOTECH_BASE_URL = "tBaseUrl";
  public final static String CONFIG_KEY_PROXY_URL = "proxyUrl";
  public final static String CONFIG_KEY_PROXY_USERNAME = "proxyUsername";
  public final static String CONFIG_KEY_PROXY_PASSWORD = "proxyPassword";
  
  
  private TrisotechClient trisotechClient;
  
  private boolean isLoggedIn = false;
  
  //
  protected Map<String, TrisotechConnectorNode> nodes = new HashMap<String, TrisotechConnectorNode>();


  @Override
  public void login(String userName, String password) {

    if (getTrisotechClient() == null) {
      ConnectorConfiguration connectorConfiguration = getConfiguration();
      init(connectorConfiguration);
    }

    setLoggedIn(getTrisotechClient().login(userName, password));
    //getNodes();
  }
  
  @Override
  public void init(ConnectorConfiguration config) {
	try{  
	    super.init(config);
	    trisotechClient = new TrisotechClient(getConfiguration().getName(), getConfiguration().getProperties().get(
	            CONFIG_KEY_TRISOTECH_BASE_URL), getConfiguration().getProperties().get(CONFIG_KEY_PROXY_URL),
	            getConfiguration().getProperties().get(CONFIG_KEY_PROXY_USERNAME), getConfiguration().getProperties()
	                .get(CONFIG_KEY_PROXY_PASSWORD));
	    setLoggedIn(false);
	}catch (URISyntaxException e) {
        e.printStackTrace();
     }
  }
  
  @Secured
  @Override
  public ConnectorNode createNode(String parentId, String label, ConnectorNodeType type, String message) {
	
	TrisotechConnectorNode parent = nodes.get(parentId);
	if(parent == null)
	{
		throw new CycleException("The parent of node '" + label + "' could not be determined.");
	}else if (parent.getId().equals(getTrisotechClient().SLASH_CHAR))
	{
		// We can't create folders from the root - they're tecnically repositories 
		throw new CycleException("New folders cannot be created from the root directory");
	}
//	else if (!parent.getId().contains(getTrisotechClient().SLASH_CHAR)){
//		throw new CycleException("New folders cannot be created from the root directory");
//	}
	
	String nodeStr = "";
	
	switch(type) {
	case BPMN_FILE :
		nodeStr = getTrisotechClient().createNewEmptyFile(parent, label);
		break;
	case FOLDER :
		nodeStr = getTrisotechClient().createFolder(parent, label);
    	break;
	default :
		return null;
	}

	TrisotechConnectorNode newNode = TrisotechJson.getConnectorNode(nodeStr, getTrisotechClient().getRespositoryName(parent));
	if(newNode.getId() == null){ // checking if the node failed to be created
		
		throw new CycleException(newNode.getMessage()); // If the node failed the error message would be stored in the object
	}
	nodes.put(newNode.getId(), newNode);
	
	return newNode;

    
  }
  @Secured
  public void deleteNode(ConnectorNode node, String message) {
	  
	TrisotechConnectorNode trisoNode = nodes.get(node.getId());
	String returnStr = null;
	
	switch (trisoNode.getType()) {
	case FOLDER:
		// in this case we're going to try to delete a folder
    	if(trisoNode.getId() == getTrisotechClient().SLASH_CHAR || trisoNode.getLabel() == null )// check if it's a repository - which we cannot delete
    		throw new CycleException("Cannot Delete A Root Directory");
        
    	if(!trisoNode.getId().contains(getTrisotechClient().SLASH_CHAR))
    		throw new CycleException("Cannot Delete A Main Directory");
    	
    	returnStr = getTrisotechClient().deleteFolder(trisoNode);   
		break;
	case BPMN_FILE:
    	returnStr = getTrisotechClient().deleteFile(trisoNode);  
		break;
	default:
		throw new CycleException("Cannot Delete: Unsupported file type");
		
	}

    // now we just need to find out if it was a success or not, if so we remvoe the node from the hashmap, if not we send back the error message
    TrisotechConnectorNode nodeStatus = TrisotechJson.getConnectorNode(returnStr, getTrisotechClient().getRespositoryName(trisoNode));
    if(nodeStatus.getId() == null)
    {
    	throw new CycleException(nodeStatus.getMessage()); // If there was a problem this will show the message
    }
    
    nodes.remove(node.getId());
    
  }
  
  @Secured
  @Override
  public List<ConnectorNode> getChildren(ConnectorNode parentConnector) {
	  
	  System.out.println("Getting Nodes");
	  TrisotechConnectorNode parent = nodes.get(parentConnector.getId());
	  	if(parent.getId().equals(TrisotechClient.SLASH_CHAR)) // This is the root, in this case making a call for repositories - not folders
	  	{
	  		String repStr = getTrisotechClient().getRepositories();
	  		List<TrisotechConnectorNode> children = TrisotechJson.getRepConnectorNodes(repStr, getId());
	  		addNodesToMap(children);
	  		return convertToConnectorNodes(children);
	  		
	  	}else if(parent.getType() == ConnectorNodeType.BPMN_FILE){ // a file will have no children - so we'll just return nothing.
	  		return new ArrayList<ConnectorNode>(); 
	  		
	  	}else if(parent.getType() == ConnectorNodeType.FOLDER){ // a folder (OR repository) has been found.
	  		String childrenStr = getTrisotechClient().getChildren(parent);
		    List<TrisotechConnectorNode> children = TrisotechJson.getConnectorNodeList(childrenStr, getId(), getTrisotechClient().getRespositoryName(parent));
		    addNodesToMap(children);
		    return convertToConnectorNodes(children);
	  	}
    
	    return null ;

  }

  private List<ConnectorNode> convertToConnectorNodes(List<TrisotechConnectorNode> children) 
  {
	  List<ConnectorNode> connectorNodeList = new ArrayList<ConnectorNode>();
	  for(TrisotechConnectorNode child : children)
		{
		  ConnectorNode newNode = new ConnectorNode(child.getId(), child.getLabel(), child.getConnectorId(), child.getType());
		  connectorNodeList.add(newNode);
		}
	  return connectorNodeList;
  }

private void addNodesToMap(List<TrisotechConnectorNode> children) {
	for(TrisotechConnectorNode child : children)
	{
		nodes.put(child.getId(), child);
	}
	
}

@Secured
@Override
public InputStream getContent(ConnectorNode node) {
	if(nodes.isEmpty())
		getNodes();
	
    TrisotechConnectorNode connectorNode = nodes.get(node.getId());
    
    //ConnectorNodeType.
    
    switch (connectorNode.getType()) {
    case BPMN_FILE:
      InputStream xmlStream = getTrisotechClient().getXmlFile(connectorNode);
      return xmlStream;
    case PNG_FILE:
        InputStream xmlStream2 = getTrisotechClient().getXmlFile(connectorNode);
        return xmlStream2;
    default:
        InputStream xmlStream1 = getTrisotechClient().getXmlFile(connectorNode);
        return xmlStream1;
  }
    
  }


  private void getNodes() 
  {
	ConnectorNode rootNode = getRoot();
	getAllChildrenNodes(rootNode);
	
	//getChildren(Rootnode);
	
  }
  /*
   * This will get all children for a given node and add them to the nodes Map
   */
  private void getAllChildrenNodes(ConnectorNode child)
  {
  	
  	List<ConnectorNode> newChildren = getChildren(child);

  	for(ConnectorNode newchild : newChildren)
  	{
  		getAllChildrenNodes(newchild);
  	}
  	
  }
  
  @Secured
  @Override
  public ContentInformation getContentInformation(ConnectorNode node) {
    TrisotechConnectorNode trisoConnectorNode = nodes.get(node.getId());
    if(trisoConnectorNode == null) {
      return ContentInformation.notFound();
    }
    else {
      return new ContentInformation(true, trisoConnectorNode.getLastModified());
    }
  }

  public ConnectorNode getNode(String id) {
    return nodes.get(id);
  }
  
  @Secured
  @Override
  public ConnectorNode getRoot() {
	 //we'll define a root for the path - we still need all the repositories
	 TrisotechConnectorNode rootNode = new TrisotechConnectorNode(TrisotechClient.SLASH_CHAR, TrisotechClient.SLASH_CHAR, ConnectorNodeType.FOLDER);
	 nodes.put(rootNode.getId(), rootNode);
	 
	 return new ConnectorNode(TrisotechClient.SLASH_CHAR, TrisotechClient.SLASH_CHAR, ConnectorNodeType.FOLDER);
  }
  

  public boolean isSupportsCommitMessage() {
    return false;
  }
  

  @Override
  public boolean needsLogin() 
  {
	if(isLoggedIn())
	{
		return false;
	}else{
		return true;
	}
	  
//	String authToken = getTrisotechClient().getAuthToken();
//	if(authToken == "" || authToken == null)
//		return true;
//	  
//    return false;
  }
  
  
  
  public TrisotechClient getTrisotechClient()
  {
	  return this.trisotechClient;
  }
  
  @Secured
  @Override
  public ContentInformation updateContent(ConnectorNode node, InputStream newContent, String message) throws Exception {
    TrisotechConnectorNode trisotechConnectorNode = nodes.get(node.getId());
    
    String nodeStr = getTrisotechClient().updateContent(trisotechConnectorNode, newContent, message);
    TrisotechConnectorNode  updatedTrisotechConnectorNode = TrisotechJson.getConnectorNode(nodeStr, getTrisotechClient().getRespositoryName(trisotechConnectorNode));
    nodes.put(updatedTrisotechConnectorNode.getId(), updatedTrisotechConnectorNode);
    
    return getContentInformation(updatedTrisotechConnectorNode);
  }

public boolean isLoggedIn() {
	return isLoggedIn;
}

public void setLoggedIn(boolean isLoggedIn) {
	this.isLoggedIn = isLoggedIn;
}
  

}
