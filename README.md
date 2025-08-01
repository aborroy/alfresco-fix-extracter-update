# Asynchronous Extractor Patch (MNT‑25250)

> Fix for Alfresco Content Services 7.x / 23.x / 25.x that prevents lost or incomplete metadata updates when the legacy `extracter.Asynchronous` bean processes file updatings.

This repository ships a drop‑in **JAR module** that transparently overrides Alfresco’s default asynchronous metadata extractor with an improved implementation (`EnhancedAsynchronousExtractor`) delivered under Hyland ticket **MNT‑25250**.

## When you need this patch

If updating a file does not trigger the `extract-metadata` action on the new content, you need to apply this patch.


## Quick start

```bash
# 1. Clone and build
git clone https://github.com/your‑org/fix-extracter-updates.git
cd fix-extracter-updates
mvn clean package -DskipTests      # requires JDK 11+

# 2. Copy the resulting JAR into Alfresco
cp target/fix-extracter-updates-*.jar $ALF_HOME/modules/platform/

# 3. Restart Alfresco
```

Upon startup you should see:

```
INFO  Module 'fix-extracter-updates' overriding bean 'extracter.Asynchronous'
```

That’s it – the patch is active.

## Deployment options

### Local Tomcat install

2. Drop the built JAR to `modules/platform/`
3. Restart Tomcat

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