/** jsdom has no modal dialog; this gives `<dialog>` a showModal that opens it and a close that closes it and says so. */
export function installDialog(): void {
  Object.assign(HTMLDialogElement.prototype, {
    showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    },
    close(this: HTMLDialogElement) {
      if (this.hasAttribute('open')) {
        this.removeAttribute('open');
        this.dispatchEvent(new Event('close'));
      }
    },
  });
}
