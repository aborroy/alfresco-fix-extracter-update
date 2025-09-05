# Asynchronous Extractor Patch (MNT‑25250)

> Fix for Alfresco Content Services 7.x / 23.x / 25.x that prevents lost or incomplete metadata updates when the legacy `extracter.Asynchronous` bean processes file updatings.

This repository ships a drop‑in **JAR module** that transparently overrides Alfresco’s default asynchronous metadata extractor with an improved implementation (`EnhancedAsynchronousExtractor`) delivered under Hyland ticket **MNT‑25250**.

## When you need this patch

If updating a file does not trigger the `extract-metadata` action on the new content, you need to apply this patch.

The patch applies to the following Share UI actions:

* `Edit in Alfresco Share`: Opens the document metadata form in Share for editing with content field
* `Upload New Version`: Replaces the current file with a newly uploaded version


## Quick start

```bash
# 1. Clone and build
git clone https://github.com/aborroy/alfresco-fix-extracter-update.git
cd fix-extracter-updates
mvn clean package -DskipTests 

# 2. Copy the resulting JAR into Alfresco
cp target/fix-extracter-updates-*.jar $ALF_HOME/modules/platform/

# 3. Restart Alfresco
```

> Alternatively `fix-extracter-updates-*.jar` can be copied to `$ALF_HOME/webapps/alfresco/WEB-INF/lib` folder

Upon startup you should see:

```
INFO  [repo.module.ModuleServiceImpl] [main] Installing module 'fix-extracter-updates' version 1.1.0.
```

That’s it, the patch is active.

## Deployment options

### Local Tomcat install

1. Drop the built JAR to `modules/platform/` or `webapps/alfresco/WEB-INF/lib`
2. Restart Tomcat

### Docker Compose

```Dockerfile
# Dockerfile
FROM alfresco/alfresco-content-repository-community:23.4.0
COPY target/fix-extracter-updates-*.jar /usr/local/tomcat/modules/platform/
```

```bash
docker build -t acs-mnt-25250 .
docker-compose -f your-compose.yml up -d --build
```

## Rollback

Simply remove the JAR and restart Alfresco:

```bash
rm $ALF_HOME/modules/platform/fix-extracter-updates-*.jar
```

Because the patch only overrides a Spring bean, no repository data is modified.

## Support

This patch is provided **as‑is**.
For commercial support open a Hyland ticket quoting **MNT‑25250** or contact `support@hyland.com`.
