import type { Meta, StoryObj } from "@storybook/react";
import { Textarea } from "./textarea";
import { Label } from "./label";

const meta: Meta<typeof Textarea> = {
  title: "Components/Textarea",
  component: Textarea,
};

export default meta;
type Story = StoryObj<typeof Textarea>;

export const Default: Story = {
  args: {},
};

export const WithPlaceholder: Story = {
  render: () => (
    <div className="space-y-2">
      <Label htmlFor="message">Nachricht</Label>
      <Textarea id="message" placeholder="Schreibe deine Nachricht..." />
    </div>
  ),
};

export const Disabled: Story = {
  args: {
    placeholder: "Deaktiviert",
    disabled: true,
  },
};
