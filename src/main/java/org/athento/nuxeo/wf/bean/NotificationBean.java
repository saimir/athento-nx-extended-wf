package org.athento.nuxeo.wf.bean;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.WorkflowUtils;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.rest.DocumentHelper;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskEventNames;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by victorsanchez on 24/11/16.
 */
@Name("extWfNotificationBean")
public class NotificationBean implements Serializable {

    private Log LOG = LogFactory.getLog(NotificationBean.class);

    @In(create = true, required = false)
    protected transient CoreSession documentManager;

    @In(create = true)
    protected transient NavigationContext navigationContext;


    @Observer(value = { TaskEventNames.WORKFLOW_ENDED,
            TaskEventNames.WORKFLOW_TASK_REJECTED,
            TaskEventNames.WORKFLOW_TASK_COMPLETED,
            TaskEventNames.WORKFLOW_ABANDONED,
            TaskEventNames.WORKFLOW_CANCELED})
    public void observerAndRaiseForEvents() {
        DocumentModel document = navigationContext.getCurrentDocument();
        // Get task document from current document
        DocumentModel taskDoc = WorkflowUtils.getTaskDocument(documentManager, document);
        EventContext eventContext = new DocumentEventContext(documentManager,
                documentManager.getPrincipal(),
                document);
        Task task = taskDoc.getAdapter(Task.class);
        eventContext.setProperty("taskInstance", task);
        WorkflowUtils.initBindings(eventContext.getProperties(), eventContext.getCoreSession(), document);
        String documentNodeId = task.getVariables().get(DocumentRoutingConstants.OPERATION_STEP_DOCUMENT_KEY);
        DocumentModel documentNode =  eventContext.getCoreSession().getDocument(new IdRef(documentNodeId));
        String nodeId = (String) documentNode.getPropertyValue("rnode:nodeId");
        Map<String, Serializable> properties = eventContext.getProperties();
        String doctype = document.getType();
        if (doctype.equals("Invoice")) {
            // Set node Id to avoid loading unknown properties in preTask
            properties.put("nodeId", nodeId);
            // TODO: Right now we put into properties valid and known metatadata, but this might be
            // improved so all properties are loaded, and then the template dedices what to use
            properties.put("docTotalAmount", document.getPropertyValue("S_FACTURA:totalAmount"));
            properties.put("docSubject", document.getPropertyValue("S_FACTURA:subject"));
            if (!nodeId.equals("preTask") & (!nodeId.equals("pre-evaluation"))) {
                // In preTask, relation to the project is still not known so these next
                // properties can not be loaded
                properties.put("docProjectid", document.getPropertyValue("projectFile:projectid"));
                LOG.info("For document in navigation context: " + document.getId()
                        + ". For Node equal to: " + nodeId
                        + ", Project Docid property is: "
                        + document.getPropertyValue("projectFile:projectDocid"));
                DocumentModel project = documentManager.getDocument(new IdRef((String) document.getPropertyValue("projectFile:projectDocid")));
                properties.put("projectDocid", project.getId());
                properties.put("projectBudget", project.getPropertyValue("invoicing:budget"));
                properties.put("projectRemainingBudget", project.getPropertyValue("invoicing:remainingBudget"));
            }
        }
        if (doctype.equals("ProjectFile")) {
            // Set node Id to avoid loading unknown properties in preTask
            properties.put("nodeId", nodeId);
            // TODO: Right now we put into properties valid and known metatadata, but this might be
            // improved so all properties are loaded, and then the template dedices what to use

            properties.put("docDocid", document.getId());

            // Invoicing values

            properties.put("docBalance", document.getPropertyValue("invoicing:balance"));
            properties.put("docBonusProvider", document.getPropertyValue("invoicing:bonusProvider"));
            properties.put("docBudget", document.getPropertyValue("invoicing:budget"));
            properties.put("docBudgetBonus", document.getPropertyValue("invoicing:budgetBonus"));
            properties.put("docImputation", document.getPropertyValue("invoicing:imputation"));
            properties.put("docInvoiceComments", document.getPropertyValue("invoicing:invoiceComments"));
            properties.put("docOperativeBudget", document.getPropertyValue("invoicing:operativeBudget"));
            properties.put("docRegion", document.getPropertyValue("invoicing:region"));
            properties.put("docSociety", document.getPropertyValue("invoicing:society"));

            // Marketing values

            properties.put("docCampaignid", document.getPropertyValue("marketing:campaignid"));
            properties.put("docCampaignDescription", document.getPropertyValue("marketing:campaignDescription"));
            properties.put("docCampaignName", document.getPropertyValue("marketing:campaignName"));
            properties.put("docMaterial", document.getPropertyValue("marketing:material"));
            properties.put("docQuantity", document.getPropertyValue("marketing:quantity"));

            // ProjectFile values

            properties.put("docActivity", document.getPropertyValue("projectFile:activity"));
            properties.put("docCategory", document.getPropertyValue("projectFile:category"));
            properties.put("docEndPlannedDate", document.getPropertyValue("projectFile:endPlannedDate"));
            properties.put("docInitialid", document.getPropertyValue("projectFile:initialid"));
            properties.put("docProjectid", document.getPropertyValue("projectFile:projectid"));
            properties.put("docProjectName", document.getPropertyValue("projectFile:projectName"));
            properties.put("docProviders", document.getPropertyValue("projectFile:providers"));
            properties.put("docSolicitantid", document.getPropertyValue("projectFile:solicitantid"));
            properties.put("docStartPlannedDate", document.getPropertyValue("projectFile:startPlannedDate"));
            properties.put("docSummary", document.getPropertyValue("projectFile:summary"));

        }

        if (hasContent(document)) {
            // Set preview url
            properties.put("previewUrl", "/restAPI/athpreview/default/" + document.getId()
                    + "/file:content/?token=" + generatePreviewToken(document));
        }
        // Set back to
        properties.put("backUrl", "/");
        Event event = eventContext.newEvent("workflowChanged");
        Framework.getLocalService(EventProducer.class).fireEvent(event);
    }

    /**
     * Check for document content.
     *
     * @param document
     * @return
     */
    private boolean hasContent(DocumentModel document) {
        return document.getPropertyValue("file:content") != null;
    }

    /**
     * Generate a simple preview token based on dublincore:modified metadata.
     *
     * @param doc document
     * @return token
     */
    private String generatePreviewToken(DocumentModel doc) {
        // Encoding token
        return Base64.encodeBase64String(String.format("%s#control", doc.getChangeToken()).getBytes());
    }
}
