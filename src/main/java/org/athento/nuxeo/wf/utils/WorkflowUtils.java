package org.athento.nuxeo.wf.utils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.scripting.*;
import org.nuxeo.ecm.automation.core.scripting.Functions;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.runtime.api.Framework;

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
     * @param doc is the document
     * @return
     */
    public static DocumentModel getTaskDocument(CoreSession session, DocumentModel doc) {
        DocumentModelList tasks =
                session.query("SELECT * FROM TaskDoc WHERE nt:targetDocumentId = '" + doc.getId() + "'");
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
        return document.getPropertyValue("file:content") != null;
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
            for (String notifyUser : notifyUsers) {
                LOG.info("==" + notifyUser);
                sendEmailNotification(session, task, document, notifyUser, properties);
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
}
