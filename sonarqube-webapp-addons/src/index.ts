/*
 * SonarQube Community Branch Plugin - addons entry point.
 * Merged API: SonarQube 26.11 webapp calls addons.entitlements.* (feature-license)
 * AND addons.branches.* (branch/PR pages).
 */

import * as entitlements from './feature-license/entitlements';
import branches from './branches';

const globalAddons = {
  entitlements,
  branches,
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AddonsType = { [key: string]: any | undefined } & typeof globalAddons;
export const addons: AddonsType = { ...globalAddons };