package org.camunda.bpm.cycle.connector.trisotech;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.camunda.bpm.cycle.connector.ConnectorNodeType;
import org.camunda.bpm.cycle.exception.CycleException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class TrisotechJson {

    private static final Logger LOGGER = Logger.getLogger(TrisotechConnector.class.getName());

    // JSON properties/objects
    private static final String JSON_DATA_OBJ = "data";

    private static final String JSON_FOLDER_OBJ = "folder";

    private static final String JSON_FILE_OBJ = "file";

    private static final String JSON_ERROR_OBJ = "error";

    // JSON values
    private static final String JSON_EMAIL_VALUE = "email";

    private static final String JSON_NAME_VALUE = "name";

    private static final String JSON_ERROR_MESSAGE_VALUE = "userMessage";

    public static String extractEmail(JSONObject jsonObj) {
        try {
            String label = "";
            JSONObject repJsonObj = jsonObj.getJSONObject(JSON_DATA_OBJ);
            if (repJsonObj.has(JSON_EMAIL_VALUE)) {
                label = repJsonObj.getString(JSON_EMAIL_VALUE);
            }
            return label;
        } catch (JSONException e) {
            return null;
        }
    }

    public static String extractFolderName(JSONObject jsonObj) {
        try {
            String name = "";
            JSONObject repJsonObj = jsonObj.getJSONObject(JSON_DATA_OBJ);
            if (repJsonObj.has(JSON_FOLDER_OBJ)) {
                JSONObject folderJsonObj = repJsonObj.getJSONObject(JSON_FOLDER_OBJ);
                name = folderJsonObj.getString(JSON_NAME_VALUE);
            }
            return name;
        } catch (JSONException e) {
            throw new RuntimeException("Unable to extract node Email.", e);
        }
    }

    public static String extractFileName(JSONObject jsonObj) {
        try {
            String name = "";
            JSONObject repJsonObj = jsonObj.getJSONObject(JSON_DATA_OBJ);
            if (repJsonObj.has(JSON_FILE_OBJ)) {
                JSONObject folderJsonObj = repJsonObj.getJSONObject(JSON_FILE_OBJ);
                name = folderJsonObj.getString(JSON_NAME_VALUE);
            }
            return name;
        } catch (JSONException e) {
            throw new RuntimeException("Unable to extract node Filename.", e);
        }
    }

    public static TrisotechConnectorNode getConnectorNode(String NodeStr, Long connectorId, String repName) {

        try {
            return getConnectorNode(new JSONObject(NodeStr), connectorId, repName);
        } catch (JSONException e) {
            e.printStackTrace();
            TrisotechConnectorNode errorNode = new TrisotechConnectorNode();
            errorNode.setMessage("Execption parsing JSON - It may have have been empty");
            return errorNode;
        }

    }

    private static TrisotechConnectorNode getConnectorNode(JSONObject obj, Long connectorId, String repName) throws JSONException {
        TrisotechConnectorNode connectorNode = new TrisotechConnectorNode();
        connectorNode.setRepositoryId(repName);
        connectorNode.setConnectorId(connectorId);
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
            jsonObj = obj.getJSONObject(JSON_FILE_OBJ);
        }
        // check if this is a folder object
        else if (obj.has(JSON_FOLDER_OBJ)) {
            connectorNode.setType(ConnectorNodeType.FOLDER);
            jsonObj = obj.getJSONObject(JSON_FOLDER_OBJ);
        }
        // check if this is a data object - in this case what we're actually looking for is in fact inside the object itself so we'll call this method again
        else if (obj.has(JSON_DATA_OBJ)) {
            Object tempObject = obj.get(JSON_DATA_OBJ);
            if (tempObject instanceof JSONObject) {
                return getConnectorNode((JSONObject) tempObject, connectorId, repName);

            } else if (tempObject instanceof JSONArray) { // the method is only looking for one connector node so we're just going to get the first value in the
                                                          // array
                return getConnectorNode(((JSONArray) tempObject).getJSONObject(0), connectorId, repName);
            }
        } else {
            connectorNode.setMessage("Unsuppoted Object Type");
            return connectorNode;
        }

        if (jsonObj.has("id")) {
            // Folders ids are not unique across repositories
            connectorNode.setId(repName + ":" + jsonObj.getString("id"));
            connectorNode.setTrisotechId(jsonObj.getString("id"));

        }
        if (jsonObj.has("path")) {
            connectorNode.setPath(jsonObj.getString("path"));
        }
        if (jsonObj.has("message")) { // this is no message - might remove this
            connectorNode.setMessage(jsonObj.getString("message"));
        }
        if (jsonObj.has("updated")) {
            String updated = jsonObj.getString("updated");
            try {
                connectorNode.setCreated(ISO8601DateFormat.INSTANCE.parse(updated));
                connectorNode.setLastModified(ISO8601DateFormat.INSTANCE.parse(updated));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        if (jsonObj.has("name")) {
            connectorNode.setLabel(jsonObj.getString("name"));
        }
        if (jsonObj.has("url")) {
            connectorNode.setURL(jsonObj.getString("url"));
        }

        LOGGER.fine("Identified node" + connectorNode);

        return connectorNode;
    }

    private static String getErrorMessage(JSONObject obj) throws JSONException {

        JSONArray jsonArr = obj.getJSONArray(JSON_ERROR_OBJ);
        String errors = "Error: ";
        for (int i = 0; i < jsonArr.length(); i++) {
            JSONObject newObj = jsonArr.getJSONObject(i);
            if (newObj.has(JSON_ERROR_MESSAGE_VALUE)) {
                errors = errors + newObj.getString(JSON_ERROR_MESSAGE_VALUE) + " ";
            }
        }

        return errors;
    }

    public static List<TrisotechConnectorNode> getConnectorNodeList(String childrenStr, Long id, String repName) {

        List<TrisotechConnectorNode> connectorNodeList = new ArrayList<TrisotechConnectorNode>();

        try {
            JSONObject jsonOb = new JSONObject(childrenStr);

            if (jsonOb.has(JSON_ERROR_OBJ)) {
                String errorMessage = getErrorMessage(jsonOb);
                throw new CycleException(errorMessage);
            }

            JSONArray arr = jsonOb.getJSONArray(JSON_DATA_OBJ);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                TrisotechConnectorNode node = getConnectorNode(obj, id, repName);
                node.setConnectorId(id);
                connectorNodeList.add(node);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return connectorNodeList;

    }

    public static List<TrisotechConnectorNode> getRepConnectorNodes(String repStr, Long connectorId) {
        List<TrisotechConnectorNode> connectorNodeList = new ArrayList<TrisotechConnectorNode>();

        try {
            JSONObject jsonOb = new JSONObject(repStr);

            if (jsonOb.has(JSON_ERROR_OBJ)) {
                String errorMessage = getErrorMessage(jsonOb);
                throw new CycleException(errorMessage);
            }

            JSONArray arr = jsonOb.getJSONArray(JSON_DATA_OBJ);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String repositoryId = obj.getString("id");
                TrisotechConnectorNode node = new TrisotechConnectorNode(repositoryId + ":" + TrisotechClient.SLASH_CHAR, connectorId,
                        ConnectorNodeType.FOLDER);
                node.setTrisotechId(repositoryId);
                node.setRepositoryId(repositoryId);
                node.setPath(TrisotechClient.SLASH_CHAR);
                node.setLabel(obj.getString("name"));
                connectorNodeList.add(node);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return connectorNodeList;
    }

}
