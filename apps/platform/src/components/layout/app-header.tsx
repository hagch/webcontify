"use client";

import React from "react";
import {
  Breadcrumb,
  BreadcrumbEllipsis,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export interface BreadcrumbSegment {
  label: string;
  href?: string;
  /** Optional extra content rendered after the label (e.g. badges) */
  suffix?: React.ReactNode;
}

type PageHeaderProps = {
  actions?: React.ReactNode;
} & (
  | { mode: "list"; title: string; description?: string; breadcrumbs?: never }
  | {
      mode: "detail";
      breadcrumbs: BreadcrumbSegment[];
      title?: never;
      description?: never;
    }
);

function CollapsibleBreadcrumbs({
  segments,
}: {
  segments: BreadcrumbSegment[];
}) {
  const shouldCollapse = segments.length > 3;
  const firstSegment = segments[0];
  const lastSegment = segments[segments.length - 1];
  const middleSegments = segments.slice(1, segments.length - 1);

  if (!shouldCollapse) {
    return (
      <Breadcrumb>
        <BreadcrumbList>
          {segments.map((segment, index) => {
            const isLast = index === segments.length - 1;
            return (
              <React.Fragment key={segment.label}>
                {index > 0 && <BreadcrumbSeparator />}
                <BreadcrumbItem>
                  {isLast ? (
                    <span className="flex items-center gap-2">
                      <BreadcrumbPage>{segment.label}</BreadcrumbPage>
                      {segment.suffix}
                    </span>
                  ) : (
                    <BreadcrumbLink href={segment.href}>
                      {segment.label}
                    </BreadcrumbLink>
                  )}
                </BreadcrumbItem>
              </React.Fragment>
            );
          })}
        </BreadcrumbList>
      </Breadcrumb>
    );
  }

  return (
    <Breadcrumb>
      <BreadcrumbList>
        {/* First segment */}
        <BreadcrumbItem>
          <BreadcrumbLink href={firstSegment.href}>
            {firstSegment.label}
          </BreadcrumbLink>
        </BreadcrumbItem>

        <BreadcrumbSeparator />

        {/* Collapsed middle segments */}
        <BreadcrumbItem>
          <DropdownMenu>
            <DropdownMenuTrigger
              className="flex items-center gap-1"
              aria-label="Show more breadcrumbs"
            >
              <BreadcrumbEllipsis />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              {middleSegments.map((segment) => (
                <DropdownMenuItem key={segment.label}>
                  {segment.href ? (
                    <a href={segment.href}>{segment.label}</a>
                  ) : (
                    <span>{segment.label}</span>
                  )}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </BreadcrumbItem>

        <BreadcrumbSeparator />

        {/* Last segment */}
        <BreadcrumbItem>
          <span className="flex items-center gap-2">
            <BreadcrumbPage>{lastSegment.label}</BreadcrumbPage>
            {lastSegment.suffix}
          </span>
        </BreadcrumbItem>
      </BreadcrumbList>
    </Breadcrumb>
  );
}

export function AppHeader(props: PageHeaderProps) {
  return (
    <header className="sticky top-0 z-10 flex min-h-14 shrink-0 items-center justify-between gap-2 border-b bg-background px-4">
      <div className="flex flex-col justify-center">
        {props.mode === "list" ? (
          <>
            <h1 className="text-lg font-semibold">{props.title}</h1>
            {props.description && (
              <p className="text-sm text-muted-foreground">
                {props.description}
              </p>
            )}
          </>
        ) : (
          <CollapsibleBreadcrumbs segments={props.breadcrumbs} />
        )}
      </div>
      {props.actions && (
        <div className="flex items-center gap-2">{props.actions}</div>
      )}
    </header>
  );
}
