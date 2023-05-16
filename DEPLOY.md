# Deploying regola to maven central

This guide is for maintainers of the regola project only.

## Pre-requisites

- Have release access to sonatype for `com.adobe.abp`
- `brew install gpg` (follow the [sonatype guide](https://central.sonatype.org/publish/requirements/gpg/))

### Pre-Merge Checks

Before merging the following should be run to ensure that a release would be successful from a build, test and documentation point of view:

```sh
mvn clean install
```

### Snapshot Releases

```sh
mvn clean deploy
```

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

As per [sonatype docs](https://central.sonatype.org/publish/publish-guide/#releasing-to-central):

> Upon release, your component will be published to Central: this typically occurs within 30 minutes, 
> though updates to search can take up to four hours.
