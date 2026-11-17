import js from '@eslint/js';
import { defineConfig } from 'eslint/config';
import prettier from 'eslint-config-prettier';
import boundaries from 'eslint-plugin-boundaries';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import simpleImportSort from 'eslint-plugin-simple-import-sort';
import globals from 'globals';
import tseslint from 'typescript-eslint';

const allowOnly = (...types) => types.map((type) => ({ to: { element: { type } } }));

export default defineConfig(
  // schema.d.ts is generated from the backend's OpenAPI spec: never hand-edited, never linted.
  { ignores: ['dist', 'node_modules', 'src/shared/api/schema.d.ts'] },

  js.configs.recommended,
  tseslint.configs.strictTypeChecked,
  tseslint.configs.stylisticTypeChecked,
  react.configs.flat.recommended,
  react.configs.flat['jsx-runtime'],
  reactHooks.configs.flat.recommended,
  jsxA11y.flatConfigs.recommended,

  {
    languageOptions: {
      globals: globals.browser,
      parserOptions: {
        projectService: { allowDefaultProject: ['eslint.config.js'] },
        tsconfigRootDir: import.meta.dirname,
      },
    },
    settings: { react: { version: 'detect' } },
    plugins: { 'simple-import-sort': simpleImportSort },
    rules: {
      // Imports: sorted automatically (`npm run lint:fix`), type imports written as `import type`.
      'simple-import-sort/imports': 'error',
      'simple-import-sort/exports': 'error',
      '@typescript-eslint/consistent-type-imports': ['error', { fixStyle: 'inline-type-imports' }],

      // Predictable code: no leftover debugging, no loose equality, always braces, no forgotten promises.
      'no-console': ['error', { allow: ['warn', 'error'] }],
      eqeqeq: ['error', 'always'],
      curly: ['error', 'all'],
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': ['error', { checksVoidReturn: { attributes: false } }],
      '@typescript-eslint/switch-exhaustiveness-check': 'error',
      '@typescript-eslint/no-unnecessary-condition': 'error',
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],

      // Components: named exports only (one way to import them), no prop-types (TypeScript is the contract).
      'react/prop-types': 'off',
      'react/jsx-no-useless-fragment': 'error',
      'react/self-closing-comp': 'error',
      'react/jsx-boolean-value': ['error', 'never'],
      'no-restricted-exports': ['error', { restrictDefaultExports: { direct: true } }],
    },
  },

  // Architecture. src/app wires everything together; src/features/<name> is one product area; src/shared is generic.
  // Dependencies point one way: app -> features -> shared. A feature never imports another feature.
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries },
    settings: {
      // Lets the plugin resolve the '@/...' alias from tsconfig.json; without it those imports are invisible to it.
      'import/resolver': { typescript: { project: './tsconfig.json' } },
      'boundaries/include': ['src/**/*'],
      'boundaries/elements': [
        { type: 'app', pattern: 'src/app' },
        { type: 'feature', pattern: 'src/features/*', capture: ['feature'] },
        { type: 'shared', pattern: 'src/shared' },
      ],
    },
    rules: {
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            { from: { element: { type: 'app' } }, allow: allowOnly('feature', 'shared') },
            {
              from: { element: { type: 'feature' } },
              allow: [
                ...allowOnly('shared'),
                { to: { element: { type: 'feature', captured: { feature: '{{from.captured.feature}}' } } } },
              ],
            },
            { from: { element: { type: 'shared' } }, allow: allowOnly('shared') },
          ],
        },
      ],
    },
  },

  // Config files at the root are plain JavaScript run by Node. The plugins ship no types, so `any` is expected here.
  {
    files: ['*.config.{js,ts}'],
    languageOptions: { globals: globals.node },
    rules: {
      'no-restricted-exports': 'off',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
    },
  },

  // Must stay last: turns off every formatting rule that would fight Prettier.
  prettier,
);
