# GraphQL Android Codegen

This sub-directory handles the introspection schema generation for the Email Android SDK.

For Android, a Gradle plugin from Apollo is used to do the actual code generation but it only takes an introspection schema in JSON format as input so we need
to generate this first.

## Installation

To use the code generation, run `yarn install` first from this subdirectory. This will ensure that all the necessary packages are installed in your local repository.

## Running

The code generation will automatically generate the file to its destination, just run `yarn codegen`. See `codegen.yml` for this configuration.

## Updates

Whenever the schema is updated, the schema documents in the `schema` sub-directory of this directory should be updated and the introspection schema updated.

Whenever the set or definition of queries, mutations, or subscriptions need to be updated, they should be updated in place under `sudoemail/src/main/graphql/com/sudoplatform/sudoemail/documents` and the introspection schema updated.

