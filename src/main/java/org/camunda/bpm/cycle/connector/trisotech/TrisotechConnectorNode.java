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

import java.util.Date;

import org.camunda.bpm.cycle.connector.ConnectorNode;
import org.camunda.bpm.cycle.connector.ConnectorNodeType;

/**
 * A connector Node which stores the content of the node
 *
 */
public class TrisotechConnectorNode extends ConnectorNode {

    private static final long serialVersionUID = 1L;

    private String fileLocation = "";

    private String trisotechPath = "";

    protected byte[] content;

    public TrisotechConnectorNode(String label, long connectorId) {
        super(label, label, connectorId, ConnectorNodeType.FOLDER);
    }

    public TrisotechConnectorNode(String label, String name, ConnectorNodeType nodeType) {
        super(label, name, nodeType);
    }

    public TrisotechConnectorNode(String label, long connectorId, ConnectorNodeType nodeType) {
        super(label, label, connectorId, nodeType);
    }

    public TrisotechConnectorNode() {
        // TODO Auto-generated constructor stub
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.setLastModified(new Date());
        this.content = content;
    }

    public String getTrisotechPath() {
        return trisotechPath;
    }

    public void setTrisotechPath(String trisotechPath) {
        this.trisotechPath = trisotechPath;
    }

    public String getFileLocation() {
        return fileLocation;
    }

    public void setFileLocation(String fileLocation) {
        this.fileLocation = fileLocation;
    }

}
