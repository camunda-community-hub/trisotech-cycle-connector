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

    private static final long serialVersionUID = 2L;

    protected byte[] content;

    protected String repositoryId;

    protected String trisotechId;

    protected String path;

    protected String url;

    public TrisotechConnectorNode(String label, long connectorId) {
        super(label, label, connectorId, ConnectorNodeType.FOLDER);
    }

    public TrisotechConnectorNode(String id, String name, ConnectorNodeType nodeType) {
        super(id, name, nodeType);
    }

    public TrisotechConnectorNode(String label, Long connectorId, ConnectorNodeType nodeType) {
        super(label, label, connectorId, nodeType);
    }

    public TrisotechConnectorNode() {
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.setLastModified(new Date());
        this.content = content;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String id) {
        this.repositoryId = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setURL(String url) {
        this.url = url;
    }

    public String getURL() {
        return url;
    }

    public String getTrisotechId() {
        return trisotechId;
    }

    public void setTrisotechId(String id) {
        this.trisotechId = id;
    }

    @Override
    public String toString() {
        return "Id: " + getId() + ", Repository:" + getRepositoryId() + ", Path:" + getPath() + ", Name:" + getLabel() + ", Trisotech Id:" + getTrisotechId()
                + ", URL:" + getURL();
    }

}
