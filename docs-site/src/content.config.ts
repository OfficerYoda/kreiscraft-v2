import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';
import { docsSchema } from '@astrojs/starlight/schema';

const docs = defineCollection({
  loader: glob({
    pattern: ['index.md', 'tutorials/**/*.md', 'how-to/**/*.md', 'reference/**/*.md', 'explanation/**/*.md'],
    base: '../docs',
  }),
  schema: docsSchema(),
});

export const collections = { docs };
