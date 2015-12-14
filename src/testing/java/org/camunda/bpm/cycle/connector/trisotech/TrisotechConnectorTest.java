package org.camunda.bpm.cycle.connector.trisotech;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import javax.imageio.stream.FileImageInputStream;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.cycle.connector.ConnectorNode;
import org.camunda.bpm.cycle.connector.ConnectorNodeType;
import org.camunda.bpm.cycle.connector.ContentInformation;
import org.camunda.bpm.cycle.exception.CycleException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
  locations = {"classpath:/spring/test/trisotech-connector-xml-config.xml"}
)
public class TrisotechConnectorTest {
	
	 @Inject
	  private TrisotechConnector trisotechConnector;
	 
	 @Before
	  public void setUp() throws Exception {

		 getTrisotechConnector().init(getTrisotechConnector().getConfiguration());
	    if (getTrisotechConnector().needsLogin()) {
	      try {
	    	  getTrisotechConnector().login(getTrisotechConnector().getConfiguration().getGlobalUser(),
	    			  getTrisotechConnector().getConfiguration().getGlobalPassword());
	      } catch (Exception e) {
	    	  e.printStackTrace();
	        fail("Something went wrong: Login failed with following exception: " + e.getMessage());
	      }
	    }
	  }
	 
	 // This will check if the login worked correctly
	  @Test
	  public void testGetAuthToken() { 
	    String authToken = getTrisotechConnector().getTrisotechClient().getAuthToken();
	    System.out.println(authToken);
	    assertNotNull(authToken);
	  }
	  
	  
	  @Test
	  @Ignore
	  public void testGetRootNode() {
	    ConnectorNode root = getTrisotechConnector().getRoot();
	    assertNotNull(root);
	    //System.out.println(root.toString());
	    //assertNotNull(reps);
	  }
	  
	  // This will recursively run through each folder starting with the root and print off the children of the folder. 
	  @Test 
	  public void testGetChildren() {
	    ConnectorNode root = getTrisotechConnector().getRoot();
	    assertNotNull(root);
	    
	    printAllChildren(root);
	    
	  }
	  
	  private void printAllChildren(ConnectorNode child)
	    {
	    	System.out.println();
	    	List<ConnectorNode> newChildren = getTrisotechConnector().getChildren(child);
	    	assertNotNull(newChildren);
	    	System.out.println("Children of "+ child.getId() + " " + newChildren.toString());
	    	
	    	for(ConnectorNode newchild : newChildren)
	    	{
	    		printAllChildren(newchild);
	    	}
	    	
	    	
	    	System.out.println();
	    	
	    }


	  
	  @Test
	  public void testGetBPMNFile()
	  {
		  ConnectorNode fileNode = getTrisotechConnector().getNode("|personal|/CreatedByRest.bpmn"); // there will always be a repository called "|personal|", but the process.bpmn must be created
		  assertNotNull(fileNode);
		  //getTrisotechConnector
		  InputStream initialStream = getTrisotechConnector().getContent(fileNode);
		  assertNotNull(initialStream);

//		  Commented out because it's not really needed for the test - but might be useful if you want to make sure the file is correct	  
//	      saveFile("C:\\tmp\\process.bpmn", initialStream);

	  }
	  
	  private void saveFile(String filename, InputStream initialStream){
		  //String filename = "C:\\tmp\\process.bpmn";
		  byte[] buffer = new byte[8 * 1024];
		  try{
			  try {
				  OutputStream output = new FileOutputStream(filename);
				  try {
				    int bytesRead;
				    while ((bytesRead = initialStream.read(buffer)) != -1) {
				      output.write(buffer, 0, bytesRead);
				    }
				  } finally {
				    output.close();
				  }
				} finally {
					initialStream.close();
				}
		  }catch(Exception e){
				e.printStackTrace();
			}
	  }
	  
	  @Test
	  @Ignore
	  public void testDeleteBPMNFile()
	  {
		  ConnectorNode fileNode = getTrisotechConnector().getNode("|personal|/process.bpmn"); // there will always be a repository called "|personal|", but the process.bpmn must be created
		  assertNotNull(fileNode);
		  //getTrisotechConnector
		  getTrisotechConnector().deleteNode(fileNode, "");
		  
		  fileNode = getTrisotechConnector().getNode("|personal|/process.bpmn");
		  assertNull(fileNode);

	//	  Commented out because it's not really needed for the test - but might be useful if you want to make sure the file is correct	  
//	      saveFile("C:\\tmp\\process.bpmn", initialStream);

	  }
	  
	  
	  @Test
	  public void createNewBPMNFile()
	  { 
		  
		   ConnectorNode parentNode = getTrisotechConnector().getNode("|personal|/test1"); // there will always be a repository called "|personal|", but the process.bpmn must be created
		   assertNotNull(parentNode);
		  //getTrisotechConnector
		  
		  ConnectorNode newFile = new ConnectorNode("|personal|/test1/CreatedByRest.bpmn", "CreatedByRest.bpmn", new Long(0), ConnectorNodeType.BPMN_FILE);
		  
		  try{
		  ConnectorNode file = getTrisotechConnector().createNode(parentNode.getId(), newFile.getLabel(), newFile.getType(), "");
		  assertNotNull(file);
		  }catch(CycleException cy)
			{
				System.out.println(cy.getMessage());
			}
//		  Commented out because it's not really needed for the test - but might be useful if you want to make sure the file is correct	  
//	      saveFile("C:\\tmp\\process.bpmn", initialStream);

	  }
	  
	  @Test
	  public void testUpdateBPMNFile()
	  {
		  ConnectorNode fileNode = getTrisotechConnector().getNode("|personal|/test1/UpdatedByRest.bpmn"); // there will always be a repository called "|personal|", but the process.bpmn must be created
		  assertNotNull(fileNode);
		  
		  try {
			  
				InputStream updateStream = new FileInputStream("C:\\tmp\\processRest.bpmn");
				assertNotNull(updateStream);
				  
				ContentInformation nodeInfo = getTrisotechConnector().updateContent(fileNode, updateStream, "");
				System.out.println(nodeInfo.toString());
				
			}catch(CycleException cy)
			{
				System.out.println(cy.getMessage());
			}
		  	catch (Exception e1) {
				e1.printStackTrace();
			}
	  }
	  
	  @Test
	  @Ignore
	  public void testCreateFolderNode() {
	    ConnectorNode root = getTrisotechConnector().getRoot();
	    assertNotNull(root);
	    String newfolderName = "createdViaRest";
	    try {
	    	// This should fail and throw a cycle exception
	    	getTrisotechConnector().createNode(root.getId(), newfolderName, ConnectorNodeType.FOLDER, "");
	
		} catch (CycleException e) {
				System.out.println(e.getMessage());
		}
	    ConnectorNode rep = getTrisotechConnector().getNode("|personal|"); // every user will have a default repository called "|personal|" so we can be sure it exists
	    assertNotNull(rep);
	    try{
	    	ConnectorNode node = getTrisotechConnector().createNode(rep.getId(), newfolderName, ConnectorNodeType.FOLDER, "");
	    } catch (CycleException e) {
			System.out.println(e.getMessage());
	    }
	  }
	  
	  @Test
	  @Ignore
	  public void testDeleteFolderNode() {
		  String newfolderName = "createdViaRest";
		  String repositoryName = "|personal|";
		  String nodeId = repositoryName+"/"+newfolderName;
		  
	    ConnectorNode root = getTrisotechConnector().getRoot();
	    assertNotNull(root);
	    
	    try {
	    	// This should fail and throw a cycle exception - can't delete root
	    	getTrisotechConnector().deleteNode(root, "");
	
		} catch (CycleException e) {
				System.out.println(e.getMessage());
		}
	    ConnectorNode rep = getTrisotechConnector().getNode(repositoryName); // every user will have a default repository called "|personal|" so we can be sure it exists
	    assertNotNull(rep);
	    try {
	    	// This should fail and throw a cycle exception - can't delete a repository
	    	getTrisotechConnector().deleteNode(rep, "");

		} catch (CycleException e) {
				System.out.println(e.getMessage());
		}
	    ConnectorNode folderNode = getTrisotechConnector().getNode(nodeId);
	    assertNotNull(folderNode);
	    try {
	    	
	    	getTrisotechConnector().deleteNode(folderNode, "");
	    	
	    } catch (CycleException e) {
			System.out.println(e.getMessage());
	    }
	    
	    ConnectorNode deletedFolderNode = getTrisotechConnector().getNode(nodeId);
	    assertNull(deletedFolderNode);
	  }
	 
	 public TrisotechConnector getTrisotechConnector()
	 {
		 return this.trisotechConnector;
	 }
}
