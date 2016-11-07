package org.athento.nuxeo.wf.operation;

import org.apache.batik.util.Platform;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.BlobCollector;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;

import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.automation.features.PlatformFunctions;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.runtime.api.Framework;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operation to send an email to task assigned user or group.
 */
@Operation(id = SendEmailAssignedTaskOperation.ID, category = "Athento", label = "Send notification email to assigned task", description = "Send notification to remember an assigned task.")
public class SendEmailAssignedTaskOperation {

    /** Log. */
    private static final Log LOG = LogFactory.getLog(SendEmailAssignedTaskOperation.class);

    /** Operation ID. */
    public static final String ID = "Athento.SendNotificationTaskAssigned";

    /** Operation context. */
    @Context
    protected OperationContext ctx;

    /** Core session. */
    @Context
    protected CoreSession session;

    @Param(name = "from", required = false)
    protected String from = "noreply@athento.com";

    @Param(name = "template", required = false)
    protected String template = "template:routingTaskAssigned";

    @Param(name = "subject", required = false)
    protected String subject;

    @Param(name = "html", required = false)
    protected boolean html = false;

    /**
     * Run operation for a document.
     *
     * @param doc is the document
     * @return the document
     * @throws Exception on error
     */
    @OperationMethod(collector = DocumentModelCollector.class)
    public DocumentModel run(DocumentModel doc) throws Exception {

        // Get document task
        DocumentModel sourceDoc = session.getDocument(new IdRef((String) doc.getPropertyValue("nt:targetDocumentId")));

        if (sourceDoc == null) {
            throw new Exception("No source document referented by task " + doc.getId());
        }

        // Check subject
        if (subject == null) {
            subject = sourceDoc.getTitle();
        }

        List<String> actors = (List<String>) doc.getPropertyValue("nt:actors");
        if (actors != null) {
            PlatformFunctions fn = new PlatformFunctions();
            StringList usernameList = new StringList();
            StringList groupList = new StringList();
            for (String act : actors) {
                if (act.startsWith("user:")) {
                    usernameList.add(act.replace("user:", ""));
                } else if (act.startsWith("group:")) {
                    groupList.add(act.replace("group:", ""));
                }
            }
            // Prepare notification
            Map<String, Object> params = new HashMap<>();
            params.put("from", from);
            params.put("message", template);
            params.put("subject", subject);
            params.put("HTML", html);

            if (!usernameList.isEmpty()) {
                LOG.info("Sending task notification to users: " + usernameList);
                params.put("to", new PlatformFunctions().getEmails(usernameList));
                // Run send email operation to username list
                runOperation("Notification.SendMail", doc, params, session);
            } else if (!groupList.isEmpty()) {
                for (String group : groupList) {
                    LOG.info("Sending task notification to group: " + group);
                    params.put("to", new PlatformFunctions().getEmailsFromGroup(group));
                    // Run send email operation to each group
                    runOperation("Notification.SendMail", doc, params, session);
                }
            }

        }

        return doc;
    }

    /**
     * Run operation.
     *
     * @param operationId
     * @param input
     * @param params
     * @param session
     * @return
     * @throws Exception
     */
    private static Object runOperation(String operationId, Object input,
                                      Map<String, Object> params, CoreSession session)
            throws Exception {
        AutomationService automationManager = Framework
                .getLocalService(AutomationService.class);
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(input);
        return automationManager.run(ctx, operationId, params);
    }
}
