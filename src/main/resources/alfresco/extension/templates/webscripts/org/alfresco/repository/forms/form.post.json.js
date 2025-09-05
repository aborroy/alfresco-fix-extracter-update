function extractMetadata(file) {
  var emAction = actions.create("extract-metadata");
  if (emAction) {
    // readOnly=false, newTransaction=false
    emAction.execute(file, false, false);
  }
}

function toNodeRefString(persistedObject, itemId) {
  // Prefer the NodeRef returned by saveForm (when kind=node).
  if (persistedObject instanceof Packages.org.alfresco.service.cmr.repository.NodeRef) {
    return persistedObject.toString();
  }
  // If the client passed a full noderef, keep it.
  if (itemId && itemId.indexOf("://") !== -1) {
    return itemId;
  }
  // Otherwise assume SpacesStore UUID.
  return "workspace://SpacesStore/" + itemId;
}

function main() {
  var itemKind = decodeURIComponent(url.templateArgs["item_kind"]);
  var itemId   = decodeURIComponent(url.templateArgs["item_id"]);

  if (typeof json === "undefined") {
    if (logger.isWarnLoggingEnabled()) logger.warn("json object was undefined.");
    status.setCode(501, "No JSON body");
    return;
  }

  var repoFormData = new Packages.org.alfresco.repo.forms.FormData();
  var jsonKeys = json.keys();
  for (; jsonKeys.hasNext();) {
    var k = jsonKeys.next();
    if (k === "alf_redirect") {
      model.redirect = json.get(k);
    } else {
      repoFormData.addFieldData(k, json.get(k));
    }
  }

  var persistedObject;
  try {
    persistedObject = formService.saveForm(itemKind, itemId, repoFormData);
  } catch (error) {
    var msg = error.message;
    if (msg.indexOf("FormNotFoundException") !== -1 ||
        msg.indexOf("PropertyValueSizeIsMoreMaxLengthException") !== -1) {
      status.setCode(404, msg);
    } else {
      status.setCode(500, msg);
    }
    return;
  }

  // Resolve the correct NodeRef and run extraction
  var nodeRefStr = toNodeRefString(persistedObject, itemId);
  var node = search.findNode(nodeRefStr);
  if (node && node.isDocument) {
    var contentProp = repoFormData.getFieldData("prop_cm_content");
    if (contentProp != null) {
      // Content was part of the form data: run extraction
      extractMetadata(node);
    } else {
      if (logger.isDebugLoggingEnabled()) {
        logger.debug("Skipping metadata extraction: no content change detected");
      }
    }
  }

  model.persistedObject = String(persistedObject);
  model.message = "Successfully persisted form for item [" + itemKind + "] " + itemId;
}

main();
