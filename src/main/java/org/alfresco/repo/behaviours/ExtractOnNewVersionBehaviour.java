package org.alfresco.repo.behaviours;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.executer.ContentMetadataExtracter;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.version.VersionServicePolicies;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.version.Version;

/**
 * Triggers the built-in "extract-metadata" action right after a new version is created.
 *
 * Why this exists:
 *  - CMIS clients often update content via check-in, which creates a new version but may not
 *    hit OnContentUpdate(newContent=true) on the live node. Binding to AfterCreateVersion
 *    ensures we run the extractor after versioning completes, regardless of protocol (CMIS, REST, etc.).
 * Scope:
 *  - Bound to cm:content (and all its subtypes) at TRANSACTION_COMMIT, so the action executes
 *    once per transaction after the version is safely persisted.
 * Notes:
 *  - This class intentionally does not implement OnContentUpdate; use it alongside your existing
 *    OnContentUpdate behavior for direct stream writes to cover all paths.
 */
public class ExtractOnNewVersionBehaviour implements VersionServicePolicies.AfterCreateVersionPolicy {

    private PolicyComponent policyComponent;
    private ActionService actionService;

    public void init() {
        policyComponent.bindClassBehaviour(
                VersionServicePolicies.AfterCreateVersionPolicy.QNAME,
                ContentModel.TYPE_CONTENT,
                new JavaBehaviour(
                        this,
                        "afterCreateVersion",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT
                )
        );
    }

    /**
     * Invoked by the policy framework after a new version has been created for a versionable node.
     *
     * @param versionableNode the live node (not the frozen version node)
     * @param version         the newly created Version object (metadata about the version)
     *
     * Implementation details:
     *  - Creates the "extract-metadata" action and runs it asynchronously on the versionable node.
     *  - The extractor will read the current content stream and update properties (title, author, etc.).
     */
    @Override
    public void afterCreateVersion(NodeRef versionableNode, Version version) {
        Action action = actionService.createAction(ContentMetadataExtracter.EXECUTOR_NAME);
        action.setExecuteAsynchronously(true);
        actionService.executeAction(action, versionableNode, true, true);
    }

    public void setPolicyComponent(PolicyComponent policyComponent) { this.policyComponent = policyComponent; }

    public void setActionService(ActionService actionService) { this.actionService = actionService; }

}