# Project-local Comcast sandbox AWS setup

This repo now includes wrappers that keep the work AWS sandbox configuration inside the project instead of touching your global `~/.aws` or `~/.aadawscli` files.

## Why this setup exists

- Your current global AWS CLI is already mapped to account `291759414836` (`guardianlink-deployer`).
- This project should use a separate Comcast work sandbox login only.
- QA should not be selected or linked for this repo.
- `aadawscli` expects `~/.aadawscli/config.json` and normally writes to `~/.aws/credentials`, so the wrappers redirect both of those locations into this repo.

## Files and folders

All local AWS state lives under:

- `.local-work-aws/home/.aadawscli/config.json`
- `.local-work-aws/home/.aws/config`
- `.local-work-aws/home/.aws/credentials`
- `.local-work-aws/work-aws.env`
- optional binary: `.local-work-aws/bin/aadawscli`

`.local-work-aws/` is gitignored.

## 1) Download `aadawscli`

Download the macOS binary that matches your machine from the Comcast release page mentioned in the internal documentation.

You can either:

- place it in your normal `PATH`, or
- place it at `.local-work-aws/bin/aadawscli`, or
- point `AAD_AWS_CLI_BIN` at the downloaded executable.

Check detection with:

```bash
./scripts/work-aws-login.sh --doctor
```

## 2) Add the Comcast `aadawscli` config locally

The internal `aadawscli-config.json` contents should be stored here:

- `.local-work-aws/home/.aadawscli/config.json`

If you already have the downloaded config file somewhere else, copy it in with:

```bash
./scripts/work-aws-login.sh --copy-config /absolute/path/to/aadawscli-config.json --doctor
```

If you prefer to copy it manually:

```bash
mkdir -p .local-work-aws/home/.aadawscli
cp /absolute/path/to/aadawscli-config.json .local-work-aws/home/.aadawscli/config.json
chmod 600 .local-work-aws/home/.aadawscli/config.json
```

## 3) Create a local sandbox settings file

Create your local settings file from the example:

```bash
mkdir -p .local-work-aws
cp config/work-aws.env.example .local-work-aws/work-aws.env
```

Then edit `.local-work-aws/work-aws.env` and set:

- `WORK_AWS_ACCOUNT_ID` once you know the sandbox account ID
- `WORK_AWS_EXPECTED_ACCOUNT_ID` to the same sandbox account ID
- `WORK_AWS_FORBIDDEN_ACCOUNT_IDS` to include:
  - `291759414836` (already set)
  - the QA account ID, once you know it
- optionally `WORK_AWS_ROLE_NAMES` and `WORK_AWS_REGION`

## 4) Sign in with `aadawscli`

Run the project-local login wrapper:

```bash
./scripts/work-aws-login.sh
```

This will:

- use `.local-work-aws/home/.aadawscli/config.json`
- write AWS credentials to `.local-work-aws/home/.aws/credentials`
- default the profile name to `work-sandbox`

When `aadawscli` shows account / role options, choose the sandbox account only. Do not choose QA.

If you hit `interaction_required`, request access to the **AWS SSO Non-Interactive CLI** application in myComcastAccess, per the internal documentation you shared.

If the browser sign-in succeeds but the terminal stops at `interaction_required` before any account or role list appears, the login has not completed and no credentials will be written. Resolve the app access first, then rerun `./scripts/work-aws-login.sh`.

## 5) Verify the active identity

Run the preflight check:

```bash
./scripts/work-aws-preflight.sh
```

The script will fail if:

- there are no project-local credentials yet
- the session is expired
- the old `aws_security_token` field exists in the credentials file
- the caller identity resolves to blocked account `291759414836`
- `WORK_AWS_EXPECTED_ACCOUNT_ID` is set and does not match the current account

## 6) Use the project-local AWS context

Run AWS CLI commands through the wrapper:

```bash
./scripts/work-aws.sh sts get-caller-identity
./scripts/work-aws.sh s3 ls
```

Run Gradle tasks that need AWS through the wrapper:

```bash
./scripts/work-gradlew.sh :server:run
./scripts/work-gradlew.sh :server:test
```

## Helpful checks

Print the resolved local paths:

```bash
./scripts/work-aws-login.sh --print-paths
./scripts/work-aws.sh --print-env
```

## Notes

- This setup does not modify your existing global AWS CLI configuration.
- It keeps the work sandbox context inside this repo only.
- Once you share the next AWS/Lambda implementation details, these same wrappers can be reused for deployment and invocation flows from the Ktor server module.

