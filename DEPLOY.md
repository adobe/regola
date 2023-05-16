# Deploying to maven central

This guide is for maintainers of the regola project only.

## Pre-requisites

- Have release access to sonatype for `com.adobe.abp`
- `brew install gpg` (follow the [sonatype guide](https://central.sonatype.org/publish/requirements/gpg/))
    - You will need to upload your public key to a keyserver with:
```sh
gpg --keyserver <server> --send-keys  <key_id_from: gpg --list-keys>

# List of keyservers to try:
- keys.openpgp.org
- keyserver.ubuntu.com
- pgp.mit.edu
```


- Create a `settings.xml` file under the `.mvn` folder with the following:

```xml
<settings>
    <servers>
        <server>
            <id>ossrh</id>
            <username>your_sonatype_username</username>
            <password>your_sonatype_password</password>
        </server>
    </servers>
    <profiles>
        <profile>
            <id>sign</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <gpg.passphrase>your_gpg_passphrase</gpg.passphrase>
            </properties>
        </profile>
    </profiles>
</settings>
```

**Note**: never share passwords or passphrases in git.

### Pre-Merge Checks

Before merging the following should be run to ensure that a release would be successful from a build, test and documentation point of view:

```sh
mvn clean install
```

### Snapshot Releases

To produce a snapshot release you should run:

```sh
mvn clean deploy
```

Upon success, you can check the [sonatype snapshot repository](https://oss.sonatype.org/content/repositories/snapshots/com/adobe/abp/regola/) to see the latest snapshot.

As per [sonatype docs](https://central.sonatype.org/publish/publish-maven/#performing-a-snapshot-deployment):

> SNAPSHOT versions are not synchronized to the Central Repository. 
> If you wish your users to consume your SNAPSHOT versions, 
> they would need to add the snapshot repository to their Nexus Repository Manager, settings.xml, or pom.xml.

### Version Releases

To produce a release you should:

- Switch to the `main` branch and ensure you have the latest `HEAD` revision
- Run `mvn release:clean release:prepare`, accepting the defaults
- Run `mvn release:perform`.

This will:
- Drop the `-SNAPSHOT` qualifier from the version number
- Create a tag in git
- Push the commit and tag to Github
- Publish the artifacts to the maven central repository
- Increase the version number and add the SNAPSHOT qualifier

Upon success, you can check the [sonatype nexus repository](https://oss.sonatype.org/#nexus-search;quick~regola) to check all the released versions.

The change will show up in the [sonatype central repository](https://central.sonatype.com/artifact/com.adobe.abp/regola) after 30 minutes or so.

As per [sonatype docs](https://central.sonatype.org/publish/publish-guide/#releasing-to-central):

> Upon release, your component will be published to Central: this typically occurs within 30 minutes, 
> though updates to search can take up to four hours.
