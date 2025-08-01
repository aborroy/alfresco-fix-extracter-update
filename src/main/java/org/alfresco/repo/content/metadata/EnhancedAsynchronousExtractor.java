package org.alfresco.repo.content.metadata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.repo.action.executer.ContentMetadataExtracter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.tagging.TaggingService;
import org.alfresco.service.namespace.NamespaceException;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;

public class EnhancedAsynchronousExtractor extends AsynchronousExtractor {

    private TransactionService transactionService;
    private NodeService nodeService;
    private NamespacePrefixResolver namespacePrefixResolver;
    private TaggingService taggingService;

    private final ObjectMapper jsonObjectMapper = new ObjectMapper();

    @Override
    public void setMetadata(NodeRef nodeRef, InputStream transformInputStream)
    {
        if (logger.isTraceEnabled())
        {
            logger.trace("Update metadata on " + nodeRef);
        }

        Map<String, Serializable> metadata = readMetadata(transformInputStream);
        if (metadata == null)
        {
            return; // Error state.
        }

        // Remove well know entries from the map that drive how the real metadata is applied.

        //--------------------------------------------
        // NOTE: This line is the only change required, move from OverwritePolicy.PRAGMATIC to OverwritePolicy.EAGER
        OverwritePolicy overwritePolicy = removeOverwritePolicy(metadata, "sys:overwritePolicy", OverwritePolicy.EAGER);
        //--------------------------------------------

        Boolean enableStringTagging = removeBoolean(metadata, "sys:enableStringTagging", false);
        Boolean carryAspectProperties = removeBoolean(metadata, "sys:carryAspectProperties", true);
        List<String> stringTaggingSeparators = removeTaggingSeparators(metadata, "sys:stringTaggingSeparators",
                ContentMetadataExtracter.DEFAULT_STRING_TAGGING_SEPARATORS);
        if (overwritePolicy == null ||
                enableStringTagging == null ||
                carryAspectProperties == null ||
                stringTaggingSeparators == null)
        {
            return; // Error state.
        }

        AuthenticationUtil.runAsSystem((AuthenticationUtil.RunAsWork<Void>) () -> transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            // Based on: AbstractMappingMetadataExtracter.extract
            Map<QName, Serializable> nodeProperties = nodeService.getProperties(nodeRef);
            // Convert to system properties (standalone)
            Map<QName, Serializable> systemProperties = convertKeysToQNames(metadata);
            // Convert the properties according to the dictionary types
            systemProperties = convertSystemPropertyValues(systemProperties);
            // There is no last filter in the AsynchronousExtractor.
            // Now use the proper overwrite policy
            Map<QName, Serializable> changedProperties = overwritePolicy.applyProperties(systemProperties, nodeProperties);

            // Based on: ContentMetadataExtracter.executeImpl
            // If none of the properties where changed, then there is nothing more to do
            if (changedProperties.isEmpty())
            {
                return null;
            }
            ContentMetadataExtracter.addExtractedMetadataToNode(nodeRef, nodeProperties, changedProperties,
                    nodeService, dictionaryService, taggingService,
                    enableStringTagging, carryAspectProperties, stringTaggingSeparators);

            if (logger.isTraceEnabled())
            {
                logger.trace("Extraction of Metadata from " + nodeRef + " complete " + changedProperties);
            }

            return null;
        }, false, true));
    }

    @Override
    public void setTransactionService(TransactionService transactionService) {
        super.setTransactionService(transactionService);
        this.transactionService = transactionService;
    }

    @Override
    public void setNodeService(NodeService nodeService) {
        super.setNodeService(nodeService);
        this.nodeService = nodeService;
    }

    @Override
    public void setNamespacePrefixResolver(NamespacePrefixResolver namespacePrefixResolver) {
        super.setNamespacePrefixResolver(namespacePrefixResolver);
        this.namespacePrefixResolver = namespacePrefixResolver;
    }

    @Override
    public void setTaggingService(TaggingService taggingService) {
        super.setTaggingService(taggingService);
        this.taggingService = taggingService;
    }

    private Map<String, Serializable> readMetadata(InputStream transformInputStream)
    {
        try
        {
            TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<HashMap<String, Serializable>>() {};
            return jsonObjectMapper.readValue(transformInputStream, typeRef);
        }
        catch (IOException e)
        {
            logger.error("Failed to read metadata from transform result", e);
            return null;
        }
    }

    private OverwritePolicy removeOverwritePolicy(Map<String, Serializable> map, String key, OverwritePolicy defaultValue)
    {
        Serializable value = map.remove(key);
        if (value == null)
        {
            return defaultValue;
        }
        try
        {
            return OverwritePolicy.valueOf((String) value);
        }
        catch (IllegalArgumentException | ClassCastException e)
        {
            logger.error(key + "=" + value + " is invalid");
            return null;
        }
    }

    private Boolean removeBoolean(Map<String, Serializable> map, Serializable key, boolean defaultValue)
    {
        @SuppressWarnings("SuspiciousMethodCalls")
        Serializable value = map.remove(key);
        if (value != null &&
                (!(value instanceof String) ||
                        (!(Boolean.FALSE.toString().equals(value) || Boolean.TRUE.toString().equals(value)))))
        {
            logger.error(key + "=" + value + " is invalid. Must be " + Boolean.TRUE + " or " + Boolean.FALSE);
            return null; // no flexibility of parseBoolean(...). It is just invalid
        }
        return value == null ? defaultValue : Boolean.parseBoolean((String) value);
    }

    private List<String> removeTaggingSeparators(Map<String, Serializable> map, String key, List<String> defaultValue)
    {
        Serializable value = map.remove(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof String))
        {
            logger.error(key + "=" + value + " is invalid.");
            return null;
        }

        List<String> list = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse((String) value, CSVFormat.RFC4180))
        {
            Iterator<CSVRecord> iterator = parser.iterator();
            CSVRecord record = iterator.next();
            if (iterator.hasNext())
            {
                logger.error(key + "=" + value + " is invalid. Should only have one record");
                return null;
            }
            record.forEach(list::add);
        }
        catch (IOException | NoSuchElementException e)
        {
            logger.error(key + "=" + value + " is invalid. Must be a CSV using CSVFormat.RFC4180");
            return null;
        }
        return list;
    }

    private Map<QName, Serializable> convertKeysToQNames(Map<String, Serializable> documentMetadata)
    {
        Map<QName, Serializable> properties = new HashMap<>();
        for (Map.Entry<String, Serializable> entry : documentMetadata.entrySet())
        {
            String key = entry.getKey();
            Serializable value = entry.getValue();
            try
            {
                QName qName = QName.createQName(key);
                try
                {
                    qName.toPrefixString(namespacePrefixResolver);
                    properties.put(qName, value);
                }
                catch (NamespaceException e)
                {
                    logger.error("Error unregistered namespace in " + qName);
                }
            }
            catch (NamespaceException e)
            {
                logger.error("Error creating qName from " + key);
            }
        }
        return properties;
    }

}
