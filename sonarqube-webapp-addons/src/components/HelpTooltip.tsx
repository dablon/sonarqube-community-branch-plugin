/*
 * SonarQube Community Branch Plugin - addons shim.
 * SonarQube 26.11 removed ~sq-server-commons/sonar-aligned/components/controls/HelpTooltip.
 * Re-implemented on top of the echoes-react Tooltip so addon files keep the old API
 * (className + overlay + children).
 */

import { Tooltip } from '@sonarsource/echoes-react';
import * as React from 'react';

interface Props {
  overlay: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}

export default function HelpTooltip({ overlay, className, children }: Props) {
  return (
    <Tooltip content={overlay}>
      <span className={className}>{children}</span>
    </Tooltip>
  );
}
