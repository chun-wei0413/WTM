interface Options {
  /** Announce a close in a task of its own, as a browser does, instead of at once. */
  announceLater?: boolean;
}

/** jsdom has no modal dialog; this gives `<dialog>` a showModal that opens it and a close that closes it and says so. */
export function installDialog({ announceLater = false }: Options = {}): void {
  Object.assign(HTMLDialogElement.prototype, {
    showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    },
    close(this: HTMLDialogElement) {
      if (!this.hasAttribute('open')) return;
      this.removeAttribute('open');
      const announce = () => this.dispatchEvent(new Event('close'));
      if (announceLater) setTimeout(announce, 0);
      else announce();
    },
  });
}
