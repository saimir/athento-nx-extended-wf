package org.athento.nuxeo.wf.utils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.platform.actions.ActionContext;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.actions.jsf.JSFActionContext;
import org.nuxeo.ecm.platform.actions.seam.SeamActionContext;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.impl.ExtendedInfoImpl;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskComment;
import org.nuxeo.ecm.platform.ui.web.util.SeamContextHelper;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.runtime.api.Framework;
import org.restlet.util.DateUtils;

import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.*;

/**
 * Created by victorsanchez on 24/11/16.
 */
public final class WorkflowUtils {

    /** Log. */
    private static final Log LOG = LogFactory.getLog(WorkflowUtils.class);

    /** Extended config path. */
    public static final String CONFIG_PATH = "/ExtendedConfig";

    /**
     * Token APP.
     */
    private static final String APP_NAME = "athentoWorkflowExt";


    /**
     * Read config value.
     *
     * @param session
     * @param key
     * @return
     */
    public static <T> T readConfigValue(CoreSession session, final String key, final  Class<T> clazz) {
        final List<T> value = new ArrayList<T>(1);
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() throws ClientException {
                DocumentModel conf = session.getDocument(new PathRef(
                        WorkflowUtils.CONFIG_PATH));
                value.add(clazz.cast(conf.getPropertyValue(key)));
            }
        }.runUnrestricted();
        if (value.isEmpty()) {
            return null;
        }
        return value.get(0);
    }

    /**
     * Run an operation.
     *
     * @param operationId
     * @param input
     * @param params
     * @param session
     * @return
     * @throws Exception
     */
    public static Object runOperation(String operationId, Object input,
                                      Map<String, Object> params, CoreSession session)
            throws Exception {
        AutomationService automationManager = Framework
                .getLocalService(AutomationService.class);
        // Input setting
        OperationContext ctx = new OperationContext(session);
        ctx.putAll(params);
        ctx.setInput(input);
        return automationManager.run(ctx, operationId, params);
    }

    /**
     * Get task from source document.
     *
     * @param ctxt
     * @return task
     */
    public static Task getTaskFromDocument(EventContext ctxt) {
        return (Task) ctxt.getProperties().get("taskInstance");
    }

    /**
     * Get task id from source document.
     *
     * @param ctxt
     * @return
     */
    public static String getTaskIdFromDocument(EventContext ctxt) {
        Task task = (Task) ctxt.getProperties().get("taskInstance");
        if (task == null) {
            return null;
        }
        return task.getDocument().getId();
    }


    /**
     * Get from source document.
     *
     * @param ctxt
     * @return
     */
    public static String getNodeIdFromDocument(EventContext ctxt) {
        String nodeId = (String) ctxt.getProperties().get("nodeId");
        return nodeId;
    }

    /**
     * Get task document.
     *
     * @param session is the session
     * @param doc is the document
     * @param state is the state of task
     * @return
     */
    public static DocumentModel getTaskDocument(CoreSession session, DocumentModel doc, String state) {
        DocumentModelList tasks =
                session.query("SELECT * FROM TaskDoc WHERE nt:targetDocumentId = '" + doc.getId() + "' AND ecm:currentLifeCycleState = '" + state + "' ORDER BY dc:modified DESC");
        if (!tasks.isEmpty()) {
            return tasks.get(0);
        }
        return null;
    }


    /**
     * Generate a simple preview token based on dublincore:modified metadata.
     *
     * @param doc document
     * @return token
     */
    public static String generatePreviewToken(DocumentModel doc) {
        // Encoding token
        return Base64.encodeBase64String(String.format("%s#control", doc.getChangeToken()).getBytes());
    }

    /**
     * Check for document content.
     *
     * @param document
     * @return
     */
    public static boolean hasContent(DocumentModel document) {
        if (document.hasSchema("file")) {
            return document.getPropertyValue("file:content") != null;
        }
        return false;
    }

    /**
     * Generate access token to document based en Nuxeo Token Auth.
     *
     * @param principals
     * @return generated access token
     */
    public static HashMap<String, String> generateAccessTokensAuth(String[] principals) {
        HashMap<String, String> tokens = new HashMap<>();
        TokenAuthenticationService tokenAuthService = Framework.getService(TokenAuthenticationService.class);
        for (String principal : principals) {
            tokens.put(principal, tokenAuthService.acquireToken(principal, APP_NAME, "default", "default", "rw"));
        }
        return tokens;
    }

    /**
     * Get task principals.
     *
     * @param ctxt
     * @return
     */
    public static String[] getTaskPrincipals(EventContext ctxt) {
        List<String> actorResult = new ArrayList<String>();
        Task task = (Task) ctxt.getProperties().get("taskInstance");
        UserManager userManager = Framework.getService(UserManager.class);
        List<String> actors = task.getActors();
        for (String actor : actors) {
            if (actor.startsWith("group:")) {
                NuxeoGroup group = userManager.getGroup(actor.split(":")[1]);
                for (String user : group.getMemberUsers()) {
                    actorResult.add(user);
                }
            } else if (actor.startsWith("user:")) {
                actorResult.add(actor.split(":")[1]);
            } else {
                actorResult.add(actor);
            }
        }
        return actorResult.toArray(new String[0]);
    }

    /**
     * Init bindings.
     *
     * @param map
     * @param session
     * @param doc
     */
    public static void initBindings(Map<String, Serializable> map, CoreSession session, DocumentModel doc) {
        map.put("This", doc);
        map.put("docTitle", doc.getTitle());
        map.put("CurrentUser", (NuxeoPrincipal) session.getPrincipal());
        map.put("currentUser", (NuxeoPrincipal) session.getPrincipal());
        map.put("Env", Framework.getProperties());
    }

    /**
     * Get string from a Set.
     *
     * @param set
     * @return
     */
    public static String getStringFromSet(Set<String> set) {
        StringBuffer userBuff = new StringBuffer();
        for (Iterator<String> it = set.iterator(); it.hasNext();) {
            userBuff.append(it.next() + (it.hasNext() ? ", " : ""));
        }
        return userBuff.toString();
    }

    /**
     * Get task information.
     *
     * @param session
     * @param task
     * @param getFormVariables
     * @return
     * @throws ClientException
     */
    public static Map<String, Serializable> getTaskInfo(final CoreSession session, final Task task, final boolean getFormVariables)
            throws ClientException {
        final String routeDocId = task.getVariable(DocumentRoutingConstants.TASK_ROUTE_INSTANCE_DOCUMENT_ID_KEY);
        final String nodeId = task.getVariable(DocumentRoutingConstants.TASK_NODE_ID_KEY);
        if (routeDocId == null) {
            throw new ClientException(
                    "Can not get the source graph for this task");
        }
        if (nodeId == null) {
            throw new ClientException(
                    "Can not get the source node for this task");
        }
        final HashMap<String, Serializable> map = new HashMap<String, Serializable>();
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() throws ClientException {
                DocumentModel doc = session.getDocument(new IdRef(routeDocId));
                GraphRoute route = doc.getAdapter(GraphRoute.class);
                GraphNode node = route.getNode(nodeId);

                if (getFormVariables) {
                    map.putAll(node.getVariables());
                    map.putAll(route.getVariables());
                }
            }
        }.runUnrestricted();
        return map;
    }

    /**
     * Notify users for task. Based on the property notifyUsers.
     *
     * @param session
     * @param task
     * @param document
     * @param properties
     */
    public static void notifiyUsers(CoreSession session, Task task, DocumentModel document, Map<String, Serializable> properties) {
        if (task != null) {
            String [] notifyUsers = (String []) properties.get("notifyUsers");
            if (notifyUsers == null) {
                return;
            }
            for (String notifyUser : notifyUsers) {
                if (notifyUser != null && !notifyUser.isEmpty()) {
                    sendEmailNotification(session, task, document, notifyUser, properties);
                }
            }
        }
    }

    /**
     * Send email notification.
     *
     * @param session
     * @param task
     * @param document
     * @param user
     * @param properties
     */
    public static void sendEmailNotification(CoreSession session, Task task, DocumentModel document, String user, Map<String, Serializable> properties) {
        try {
            if (user == null || user.isEmpty()) {
                return;
            }
            LOG.info("Sending email notification to " + user);
            Map<String, Object> params = new HashMap<>();
            params.putAll(properties);
            params.put("taskId", task.getId());
            params.put("toUser", user.trim());
            params.put("template", "template:workflowTaskAssignedGeneric");
            params.put("subject", "[Nuxeo]Task assigned " + document.getName());
            params.put("html", true);
            WorkflowUtils.runOperation("Athento.SendNotificationTaskAssigned", document, params, session);
        } catch (Exception e) {
            LOG.error("Error sending notification to notify user: " + user, e);
            throw new ClientException(e);
        }
    }

    /**
     * New log entry.
     *
     * @param doc
     * @param principal
     * @param event
     * @param category
     * @param comment
     * @param directive
     * @return
     */
    public static LogEntry newEntry(DocumentModel doc, String principal, String event, String category, String comment, ExtendedInfo directive, ExtendedInfo dueDate) {
        AuditLogger auditLogger = Framework.getService(AuditLogger.class);
        LogEntry entry = auditLogger.newLogEntry();
        entry.setEventId(event);
        entry.setEventDate(new Date());
        entry.setCategory(category);
        entry.setDocUUID(doc.getId());
        entry.setDocPath(doc.getPathAsString());
        entry.setComment(comment);
        entry.setPrincipalName(principal);
        entry.setDocType(doc.getType());
        entry.setRepositoryId(doc.getRepositoryName());
        if (directive != null) {
            entry.getExtendedInfos().put("directive", directive);
        }
        if (dueDate != null){
            entry.getExtendedInfos().put("dueDate", dueDate);
        }
        try {
            entry.setDocLifeCycle(doc.getCurrentLifeCycleState());
        } catch (Exception e) {
            // ignore error
        }
        auditLogger.addLogEntries(Collections.singletonList(entry));
        return entry;
    }

    /**
     * Create audit change.
     */
    public static void createAuditChange(DocumentModel document, Task task, String user) {
        String taskName = task.getType();
        List<TaskComment> comments = task.getComments();
        String comment = "";
        if (comments == null || comments.isEmpty()) {
            comment = "Workflow changed by " + user;
        } else {
            TaskComment taskComment = comments.get(0);
            comment = taskComment.getText() + " at "
                    + DateUtils.format(taskComment.getCreationDate().getTime(), "yyyy-MM-dd HH:mm");
        }
        ExtendedInfoImpl.StringInfo directive = (ExtendedInfoImpl.StringInfo) ExtendedInfoImpl.createExtendedInfo(task.getDirective() != null && !task.getDirective().isEmpty() ? task.getDirective() : task.getType());
        ExtendedInfoImpl.DateInfo dueDate = (ExtendedInfoImpl.DateInfo) ExtendedInfoImpl.createExtendedInfo(task.getDueDate());
        WorkflowUtils.newEntry(document, user,
                taskName, WorkflowExtConstants.WORKFLOW_CATEGORY, comment, directive, dueDate);
    }

    /**
     * Get route model.
     *
     * @param document
     * @param session
     * @return
     */
    public static DocumentModel getRouteModelForDocument(DocumentModel document, CoreSession session) {
        DocumentRoutingService documentRoutingService = Framework.getLocalService(DocumentRoutingService.class);
        List<DocumentModel> routeModels = documentRoutingService.searchRouteModels(
                session, "");
        for (Iterator<DocumentModel> it = routeModels.iterator(); it.hasNext(); ) {
            DocumentModel route = it.next();
            Object graphRouteObj = route.getAdapter(GraphRoute.class);
            if (graphRouteObj instanceof GraphRoute) {
                String filter = ((GraphRoute) graphRouteObj).getAvailabilityFilter();
                if (!StringUtils.isBlank(filter)) {
                    if (checkActionFilter(document, session, filter)) {
                        return route;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check action filter.
     *
     * @param document
     * @param session
     * @param filterId
     * @return
     */
    private static boolean checkActionFilter(DocumentModel document, CoreSession session, String filterId) {
        ActionManager actionService = Framework.getService(ActionManager.class);
        return actionService.checkFilter(filterId, createActionContext(document, session));
    }

    /**
     * Create action context.
     *
     * @param document
     * @param session
     * @return
     */
    private static ActionContext createActionContext(DocumentModel document, CoreSession session) {
        ActionContext ctx;
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null) {
            ctx = new SeamActionContext();
        } else {
            ctx = new JSFActionContext(faces);
        }
        ctx.setCurrentDocument(document);
        ctx.setDocumentManager(session);
        ctx.setCurrentPrincipal((NuxeoPrincipal) session.getPrincipal());
        ctx.putLocalVariable("SeamContext", new SeamContextHelper());
        return ctx;
    }

    /**
     * Get document node from task.
     *
     * @param session
     * @param task
     * @return
     */
    public static DocumentModel getDocumentNodeFromTask(CoreSession session, Task task) {
        String nodeStepId = task.getVariable("document.routing.step");
        return session.getDocument(new IdRef(nodeStepId));
    }

    /**
     * Get target document given the task.
     *
     * @param task
     * @return
     */
    public static DocumentModel getTargetDocumentFromTask(CoreSession session, Task task) {
        return session.getDocument(new IdRef(task.getTargetDocumentId()));
    }

    /**
     * Clear comment property.
     *
     * @param node
     */
    public static void clearComments(CoreSession session, GraphNode node) {
        try {
            LOG.info("Clearing comments for " + node.getDocument().getId());
            node.getDocument().setPropertyValue("var-" + node.getId() + ":comment", "");
            session.saveDocument(node.getDocument());
        } catch (PropertyException e) {
            LOG.warn("Node " + node.getId() + " has not comment property");
        }
    }
}
