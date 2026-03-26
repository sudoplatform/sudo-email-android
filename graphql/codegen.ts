import type { CodegenConfig } from '@graphql-codegen/cli'

const config: CodegenConfig = {
  overwrite: true,
  schema: ['./schema/**/*.graphql'],
  documents: [
    '../sudoemail/src/main/graphql/com/sudoplatform/sudoemail/documents/**/*.graphql',
  ],
  generates: {
    '../sudoemail/src/main/graphql/com/sudoplatform/sudoemail/schema.json': {
      plugins: ['introspection'],
    },
  },
  ignoreNoDocuments: true,
}

export default config
