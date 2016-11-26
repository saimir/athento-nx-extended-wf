package org.athento.nuxeo.wf.utils;

import org.apache.commons.codec.binary.Base64;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.scripting.*;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.EventContext;
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
}
