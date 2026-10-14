import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://docs.kreiscraft.de',
  markdown: {
    processedDirs: ['../docs'],
  },
  integrations: [
    starlight({
      title: 'Kreiscraft',
      description: 'Documentation for the Kreiscraft Paper plugin.',
      favicon: '/crafting-table.png',
      customCss: ['./src/styles/themes.css'],
      components: {
        Head: './src/components/Head.astro',
      },
      sidebar: [
        { label: 'Overview', link: '/' },
        {
          label: 'Tutorials',
          items: [
            { label: 'Getting Started', link: 'tutorials/getting-started' },
            { label: 'Your First Module', link: 'tutorials/your-first-module' },
            { label: 'Writing a Command', link: 'tutorials/writing-a-command' },
          ],
        },
        {
          label: 'How-to Guides',
          items: [
            { label: 'Add a New Module', link: 'how-to/add-a-new-module' },
            { label: 'Enable or Disable a Module', link: 'how-to/enable-or-disable-a-module' },
            { label: 'Persist Module State', link: 'how-to/persist-module-state' },
            { label: 'Add and Use Messages', link: 'how-to/add-and-use-messages' },
            { label: 'Reload Configuration', link: 'how-to/reload-configuration' },
            { label: 'Configure the HTTP API', link: 'how-to/configure-the-http-api' },
            { label: 'Write Tests', link: 'how-to/write-tests' },
          ],
        },
        {
          label: 'Reference',
          items: [
            { label: 'Module Catalog', link: 'reference/module-catalog' },
            { label: 'Commands and Permissions', link: 'reference/commands-and-permissions' },
            { label: 'Configuration', link: 'reference/configuration' },
            { label: 'HTTP and WebSocket API', link: 'reference/http-api' },
            { label: 'OpenAPI specification', link: '/reference/openapi.yaml' },
            { label: 'SDK', link: 'reference/sdk' },
            { label: 'Module Template', link: 'reference/module-template' },
          ],
        },
        {
          label: 'Explanation',
          items: [
            { label: 'Architecture', link: 'explanation/architecture' },
            { label: 'Design Decisions', link: 'explanation/design-decisions' },
          ],
        },
      ],
    }),
  ],
});
