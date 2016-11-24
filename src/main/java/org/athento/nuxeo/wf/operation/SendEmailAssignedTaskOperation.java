package org.athento.nuxeo.wf.operation;

import org.apache.bcel.verifier.structurals.Frame;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.automation.features.PlatformFunctions;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.ecm.platform.task.core.service.TaskEventNotificationHelper;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.Arrays;
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

    @Param(name = "template", required = false, values = { "template:routingTaskAssigned", "template:workflowTaskAssignedProjectFile" })
    protected String template = "template:routingTaskAssigned";

    @Param(name = "subject", required = false)
    protected String subject;

    @Param(name = "html", required = false)
    protected boolean html = false;

    @Param(name = "toUser", required = false,
            description = "If this value is not empty, the task assigned notification will be send to the this username.")
    protected String toUser;

    /**
     * Run operation for a document.
     *
     * @param doc is the document
     * @return the document
     * @throws Exception on error
     */
    @OperationMethod(collector = DocumentModelCollector.class)
    public DocumentModel run(DocumentModel doc) throws Exception {

        TaskService taskService = Framework.getService(TaskService.class);
        List<Task> documentTasks = taskService.getTaskInstances(doc, (NuxeoPrincipal) null, session);

        for (Task task : documentTasks) {
            notifyTask(task, doc);
        }

        return doc;
    }

    /**
     * Notifiy task.
     *
     * @param task
     * @param doc
     * @throws Exception
     */
    private void notifyTask(Task task, DocumentModel doc) throws Exception {
        HashMap<String, Serializable> params = new HashMap<>();
        for (Map.Entry<String, Object> entry : ctx.entrySet()) {
            if (entry.getValue() instanceof Serializable) {
                params.put(entry.getKey(), (Serializable) entry.getValue());
            }
        }
        if (LOG.isInfoEnabled()) {
            LOG.info("Sending notification for document:" + doc.getId() + " and task: " + task.getDocument().getId());
        }
        //TaskEventNotificationHelper.notifyEvent(session, doc, (NuxeoPrincipal) session.getPrincipal(),
          //      task, "workflowTaskAssigned", params, "Remember task assigned.", "");

        // Add event context to context
        ctx.putAll(params);

        // Check subject
        if (subject == null) {
            subject = doc.getTitle();
        }

        List<String> actors = task.getActors();
        if (actors != null) {
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
            Map<String, Object> mailParams = new HashMap<>();
            mailParams.put("from", from);
            mailParams.put("message", template);
            mailParams.put("subject", subject);
            mailParams.put("HTML", html);

            if (toUser != null) {
                mailParams.put("to", new PlatformFunctions().getEmail(toUser));
                // Run send email operation to the param "to" email
                runOperation("Notification.SendMail", doc, mailParams, session, ctx);
            } else {
                if (!usernameList.isEmpty()) {
                    LOG.info("Sending task notification to users: " + usernameList);
                    mailParams.put("to", new PlatformFunctions().getEmails(usernameList));
                    // Run send email operation to username list
                    runOperation("Notification.SendMail", doc, mailParams, session, ctx);
                } else if (!groupList.isEmpty()) {
                    for (String group : groupList) {
                        LOG.info("Sending task notification to group: " + group);
                        mailParams.put("to", new PlatformFunctions().getEmailsFromGroup(group));
                        // Run send email operation to each group
                        runOperation("Notification.SendMail", doc, mailParams, session, ctx);
                    }
                }
            }
        }
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
                                      Map<String, Object> params, CoreSession session, OperationContext context)
            throws Exception {
        AutomationService automationManager = Framework
                .getLocalService(AutomationService.class);
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(input);
        ctx.putAll(context);
        return automationManager.run(ctx, operationId, params);
    }
}
