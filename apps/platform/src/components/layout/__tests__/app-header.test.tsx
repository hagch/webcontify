import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AppHeader } from "../app-header";

// Mock @base-ui/react/menu used by DropdownMenu
vi.mock("@base-ui/react/menu", () => {
  const Root = ({ children }: { children: React.ReactNode }) => (
    <div data-slot="dropdown-menu">{children}</div>
  );
  const Trigger = ({
    children,
    ...props
  }: {
    children: React.ReactNode;
    [key: string]: unknown;
  }) => <button {...props}>{children}</button>;
  const Portal = ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  );
  const Positioner = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );
  const Popup = ({
    children,
    className,
  }: {
    children: React.ReactNode;
    className?: string;
  }) => <div className={className}>{children}</div>;
  const Item = ({
    children,
    className,
  }: {
    children: React.ReactNode;
    className?: string;
  }) => <div role="menuitem" className={className}>{children}</div>;
  const Group = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );
  const GroupLabel = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );
  const Separator = ({ className }: { className?: string }) => (
    <hr className={className} />
  );
  const CheckboxItem = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );
  const CheckboxItemIndicator = ({
    children,
  }: {
    children: React.ReactNode;
  }) => <span>{children}</span>;
  const RadioGroup = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );
  const RadioItem = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );
  const RadioItemIndicator = ({ children }: { children: React.ReactNode }) => (
    <span>{children}</span>
  );
  const SubmenuRoot = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );
  const SubmenuTrigger = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );

  return {
    Menu: {
      Root,
      Trigger,
      Portal,
      Positioner,
      Popup,
      Item,
      Group,
      GroupLabel,
      Separator,
      CheckboxItem,
      CheckboxItemIndicator,
      RadioGroup,
      RadioItem,
      RadioItemIndicator,
      SubmenuRoot,
      SubmenuTrigger,
    },
  };
});

// Mock @base-ui/react/use-render used by BreadcrumbLink
vi.mock("@base-ui/react/use-render", () => ({
  useRender: ({
    props,
    defaultTagName,
  }: {
    props: Record<string, unknown>;
    defaultTagName: string;
    state?: Record<string, unknown>;
    render?: unknown;
  }) => {
    const Tag = defaultTagName as keyof JSX.IntrinsicElements;
    const { className, children, href } = props as {
      className?: string;
      children?: React.ReactNode;
      href?: string;
    };
    return (
      <Tag className={className} href={href}>
        {children}
      </Tag>
    );
  },
}));

// Mock @base-ui/react/merge-props
vi.mock("@base-ui/react/merge-props", () => ({
  mergeProps: (...args: Record<string, unknown>[]) =>
    Object.assign({}, ...args),
}));

describe("AppHeader", () => {
  it("renders title in list mode", () => {
    render(<AppHeader mode="list" title="Tenants" />);
    expect(
      screen.getByRole("heading", { name: "Tenants" })
    ).toBeInTheDocument();
  });

  it("renders description in list mode", () => {
    render(
      <AppHeader mode="list" title="Tenants" description="Manage tenants" />
    );
    expect(screen.getByText("Manage tenants")).toBeInTheDocument();
  });

  it("does not render description when not provided", () => {
    render(<AppHeader mode="list" title="Tenants" />);
    expect(
      screen.queryByText("text-muted-foreground")
    ).not.toBeInTheDocument();
  });

  it("renders actions in list mode", () => {
    render(
      <AppHeader
        mode="list"
        title="Tenants"
        actions={<button>Create</button>}
      />
    );
    expect(screen.getByText("Create")).toBeInTheDocument();
  });

  it("renders breadcrumbs in detail mode", () => {
    render(
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: "Tenants", href: "/tenants" },
          { label: "Acme Corp" },
        ]}
      />
    );
    expect(screen.getByText("Tenants")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
  });

  it("renders actions in detail mode", () => {
    render(
      <AppHeader
        mode="detail"
        breadcrumbs={[{ label: "Back" }]}
        actions={<button>Edit</button>}
      />
    );
    expect(screen.getByText("Edit")).toBeInTheDocument();
  });

  it("collapses middle breadcrumbs when more than 3 segments", () => {
    render(
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: "Apps", href: "/apps" },
          { label: "Shop", href: "/apps/shop" },
          { label: "Products", href: "/apps/shop/products" },
          { label: "Sneaker XL" },
        ]}
      />
    );
    // First and last should be visible
    expect(screen.getByText("Apps")).toBeInTheDocument();
    expect(screen.getByText("Sneaker XL")).toBeInTheDocument();
    // Ellipsis trigger button should be rendered for the collapsed segments
    expect(
      screen.getByRole("button", { name: "Show more breadcrumbs" })
    ).toBeInTheDocument();
  });

  it("shows all breadcrumb segments when 3 or fewer", () => {
    render(
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: "Apps", href: "/apps" },
          { label: "Shop", href: "/apps/shop" },
          { label: "Products" },
        ]}
      />
    );
    expect(screen.getByText("Apps")).toBeInTheDocument();
    expect(screen.getByText("Shop")).toBeInTheDocument();
    expect(screen.getByText("Products")).toBeInTheDocument();
  });

  it("has sticky positioning", () => {
    render(<AppHeader mode="list" title="Test" />);
    const header = screen.getByRole("banner");
    expect(header.className).toContain("sticky");
  });

  it("renders header element with banner role", () => {
    render(<AppHeader mode="list" title="Test" />);
    expect(screen.getByRole("banner")).toBeInTheDocument();
  });
});
