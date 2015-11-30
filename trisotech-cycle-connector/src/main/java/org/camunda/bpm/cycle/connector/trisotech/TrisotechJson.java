package org.camunda.bpm.cycle.connector.trisotech;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.cycle.connector.ConnectorNode;
import org.camunda.bpm.cycle.connector.ConnectorNodeType;
import org.camunda.bpm.cycle.exception.CycleException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.jcraft.jsch.Logger;

public class TrisotechJson {
	
	  // JSON properties/objects
	  private static final String JSON_DATA_OBJ = "data";
	  private static final String JSON_FOLDER_OBJ = "folder";
	  private static final String JSON_PATH_PROP = "path";
	  private static final String JSON_FILE_OBJ = "file";
	  private static final String JSON_ERROR_OBJ = "error";

	  // JSON values
	  private static final String JSON_AUTHTOKEN_VALUE = "authToken";
	  private static final String JSON_NAME_VALUE = "name";
	  private static final String JSON_ERROR_MESSAGE_VALUE = "userMessage";
	  
	  private static final String SLASH_CHAR = "/";

	  public static String extractNodeToken(JSONObject jsonObj) {
		    try {
		      String label = "";
		      JSONObject repJsonObj = jsonObj.getJSONObject(JSON_DATA_OBJ);
		      if (repJsonObj.has(JSON_AUTHTOKEN_VALUE)) {
		        label = repJsonObj.getString(JSON_AUTHTOKEN_VALUE);
		      } 
		      return label;
		    } catch (JSONException e) {
		      throw new RuntimeException("Unable to extract node Auth Token.", e);
		    }
		  }
	  
	  public static String extractFolderName(JSONObject jsonObj) {
		    try {
		      String name = "";
		      JSONObject repJsonObj = jsonObj.getJSONObject(JSON_DATA_OBJ);
		      if (repJsonObj.has(JSON_FOLDER_OBJ)) {
		    	JSONObject folderJsonObj =  repJsonObj.getJSONObject(JSON_FOLDER_OBJ);
		        name = folderJsonObj.getString(JSON_NAME_VALUE);
		      } 
		      return name;
		    } catch (JSONException e) {
		      throw new RuntimeException("Unable to extract node Auth Token.", e);
		    }
		  }
	  
	  public static String extractFileName(JSONObject jsonObj) {
		    try {
		      String name = "";
		      JSONObject repJsonObj = jsonObj.getJSONObject(JSON_DATA_OBJ);
		      if (repJsonObj.has(JSON_FILE_OBJ)) {
		    	JSONObject folderJsonObj =  repJsonObj.getJSONObject(JSON_FILE_OBJ);
		        name = folderJsonObj.getString(JSON_NAME_VALUE);
		      } 
		      return name;
		    } catch (JSONException e) {
		      throw new RuntimeException("Unable to extract node Auth Token.", e);
		    }
		  }

	public static TrisotechConnectorNode getConnectorNode(String NodeStr, String repName) {
	
		try {
			return getConnectorNode(new JSONObject(NodeStr), repName);
		} catch (JSONException e) {
			e.printStackTrace();
			System.out.println("JSON was: "+ NodeStr);
			TrisotechConnectorNode errorNode = new TrisotechConnectorNode();
			errorNode.setMessage("Execption parsing JSON - It may have have been empty");
			return errorNode;
		}


	}

	
	private static  TrisotechConnectorNode getRepositoryConnectorNode(JSONObject obj) throws JSONException
	{
		TrisotechConnectorNode connectorNode = new TrisotechConnectorNode();

		  
			connectorNode.setType(ConnectorNodeType.FOLDER);

			    if (obj.has("id")) {
			      connectorNode.setId(obj.getString("id"));
			    }

			    if (obj.has("message")) {
			      connectorNode.setMessage(obj.getString("message"));
			    }
			    if(obj.has("name")) {
				 connectorNode.setLabel(obj.getString("name"));
			    }
			    if (obj.has("updated")) {
			      connectorNode.setLastModified(new Date(obj.getLong("updated")));
			      //connectorNode.setCreated(new Date(jsonObj.getLong("updated")));
			    }
			    
			    //Repositories in trisotech have no path given back - we need to set one in order to create new folders 
			    connectorNode.setTrisotechPath(SLASH_CHAR);
		    
		    return connectorNode;
	}
	
	  private static TrisotechConnectorNode getConnectorNode(JSONObject obj, String repName) throws JSONException {
		   TrisotechConnectorNode connectorNode = new TrisotechConnectorNode();
		   JSONObject jsonObj = new JSONObject();
	    
		    // Check if there has been an error message
		   if (obj.has(JSON_ERROR_OBJ)) {
			      String errorMessage = getErrorMessage(obj);
			      connectorNode.setMessage(errorMessage);
			      return connectorNode; 
			}
		   // Check if this is a file object
		   else if (obj.has(JSON_FILE_OBJ)) {
		      connectorNode.setType(ConnectorNodeType.BPMN_FILE);
		      jsonObj =  obj.getJSONObject(JSON_FILE_OBJ);
		    }
		   // check if this is a folder object
		    else if (obj.has(JSON_FOLDER_OBJ)) {
			      connectorNode.setType(ConnectorNodeType.FOLDER);
			      jsonObj =  obj.getJSONObject(JSON_FOLDER_OBJ);
			}
		   // check if this is a data object - in this case what we're actually looking for is in fact inside the object itself so we'll call this method again 
		    else if (obj.has(JSON_DATA_OBJ) ){
		    	Object tempObject = obj.get(JSON_DATA_OBJ);
		    	if(tempObject instanceof JSONObject){
		    		return getConnectorNode((JSONObject)tempObject , repName);
		    		
		    	}else if (tempObject instanceof JSONArray){ // the method is only looking for one connector node so we're just going to get the first value in the array
		    		return getConnectorNode((JSONArray)tempObject, repName );
		    	}
		    }
		    else {
		    	connectorNode.setMessage("Unsuppoted Object Type");
		    	return connectorNode;
		    }
	
			    if (jsonObj.has("id")) {
			      connectorNode.setId(repName + jsonObj.getString("id")); 
			   // I'm attaching the repository to the start of the ID because i'll need it when querying children.
			   // It will also ensure the ID is unique to other Nodes - without it two files in two different repositories would have the same id and there isn't a way to tell what repository they're from
			    }
			    if (jsonObj.has("path")) {
			      connectorNode.setTrisotechPath(jsonObj.getString("path"));
			    }
			    if (jsonObj.has("message")) { // this is no message - might remove this
			      connectorNode.setMessage(jsonObj.getString("message"));
			    }
			    if (jsonObj.has("updated")) {
			    	// TODO need to sort out this date object
//			      connectorNode.setLastModified(new Date(jsonObj.getLong("updated")));
//			      connectorNode.setCreated(new Date(jsonObj.getLong("updated")));
			    }
			    if (jsonObj.has("name"))
			    {
			    	connectorNode.setLabel(jsonObj.getString("name"));
			    }
			    if (jsonObj.has("url"))
			    {
			    	connectorNode.setFileLocation(jsonObj.getString("url"));
			    }
		    
		    return connectorNode;
		  }
	  
	  


	private static TrisotechConnectorNode getConnectorNode(JSONArray jsonArr, String repName) throws JSONException{
		JSONObject newObj = jsonArr.getJSONObject(0);
		return getConnectorNode(newObj, repName);
	}

	private static String getErrorMessage(JSONObject obj) throws JSONException{
		
		JSONArray jsonArr=  obj.getJSONArray(JSON_ERROR_OBJ);
		String errors = "Error: ";
		for(int i = 0; i < jsonArr.length(); i++){
			JSONObject newObj = jsonArr.getJSONObject(i);
			if (newObj.has(JSON_ERROR_MESSAGE_VALUE)) 
				errors = errors + newObj.getString(JSON_ERROR_MESSAGE_VALUE) + " ";
		}
		
		
		return errors;
	}

	public static List<TrisotechConnectorNode> getConnectorNodeList(String childrenStr,
			Long id, String repName) {

	    List<TrisotechConnectorNode> connectorNodeList = new ArrayList<TrisotechConnectorNode>();

	    try {
	      JSONObject jsonOb = new JSONObject(childrenStr);
	      
	      if (jsonOb.has(JSON_ERROR_OBJ)) {
		      String errorMessage = getErrorMessage(jsonOb);
		      throw new CycleException(errorMessage); 
	      }
	      
	      JSONArray arr =  jsonOb.getJSONArray(JSON_DATA_OBJ);
	    	
	    	
	      for (int i = 0; i < arr.length(); i++) {
	        JSONObject obj = arr.getJSONObject(i);
	        TrisotechConnectorNode node = getConnectorNode(obj, repName);
	        node.setConnectorId(id);
	        connectorNodeList.add(node);
	      }
	    } catch (JSONException e) {
	      e.printStackTrace();
	      return null;
	    }

	    return connectorNodeList;
		
	}

	public static List<TrisotechConnectorNode> getRepConnectorNodes(String repStr,
			Long id) {
		  List<TrisotechConnectorNode> connectorNodeList = new ArrayList<TrisotechConnectorNode>();

		    try {
		      JSONObject jsonOb = new JSONObject(repStr);
		      
		      if (jsonOb.has(JSON_ERROR_OBJ)) {
			      String errorMessage = getErrorMessage(jsonOb);
			      throw new CycleException(errorMessage); 
		      }
		      
		      JSONArray arr =  jsonOb.getJSONArray(JSON_DATA_OBJ);
		    	
		    	
		      for (int i = 0; i < arr.length(); i++) {
		        JSONObject obj = arr.getJSONObject(i);
		        TrisotechConnectorNode node = getRepositoryConnectorNode(obj);
		        node.setConnectorId(id);
		        connectorNodeList.add(node);
		      }
		    } catch (JSONException e) {
		      e.printStackTrace();
		      return null;
		    }

		    return connectorNodeList;
	}

	
}
