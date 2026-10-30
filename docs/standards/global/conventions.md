## Development Conventions

### Predictable Structure
Organize files and directories in a logical, navigable layout.

### Up-to-Date Documentation
Keep README files current with setup steps, architecture overview, and contribution guidelines.

### Clean Version Control
Write clear commit messages, use feature branches, and add meaningful descriptions to pull requests.

### Environment Variables
Store configuration in environment variables; never commit secrets or API keys.

### Minimal Dependencies
Keep dependencies lean and up-to-date; document why major ones are included.

### Consistent Reviews
Follow a defined code review process with clear expectations for reviewers and authors.

### Testing Standards
Define required test coverage (unit, integration, etc.) before merging.

### Feature Flags
Use flags for incomplete features instead of long-lived branches.

### Changelog Updates
Maintain a changelog or release notes for significant changes.

### Build What's Needed
Avoid speculative code and "just in case" additions (see minimal-implementation.md).

### Secrets via local.properties and BuildConfig
Secrets are never committed. They are read from the git-ignored `local.properties` at build time and injected as BuildConfig fields with an empty-string default when absent (e.g. `buildConfigField("String", "ANILIST_CLIENT_SECRET", localProps.getProperty("ANILIST_CLIENT_SECRET", ""))`); `buildFeatures { buildConfig = true }` enables this.

### Minimal Permissions
The app requests `INTERNET` and `com.android.providers.tv.permission.WRITE_EPG_DATA` (needed only to publish the Continue Watching preview channel to the TV home screen). Any new permission must be justified by a concrete feature before being added to the manifest.
